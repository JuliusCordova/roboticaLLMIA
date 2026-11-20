"""Rules and use cases. No dependencies on FastAPI, Firebase or Google Cloud."""
from __future__ import annotations

from dataclasses import dataclass, field, replace
from datetime import datetime, timedelta
from enum import StrEnum
from hashlib import sha256
import logging
from typing import Callable, Protocol, TypeVar
from uuid import uuid4


class Status(StrEnum):
    PENDING = "PENDIENTE"
    CLAIMED = "RECLAMADA"
    ANALYZING = "ANALIZANDO"
    READY = "LISTA"
    ERROR = "ERROR"
    EXPIRED = "VENCIDA"


logger = logging.getLogger(__name__)


DEFAULT_QUESTION = "¿Qué ves?"
MAX_QUESTION = 500


def validate_question(question: str) -> str:
    if not isinstance(question, str) or not question.strip() or len(question) > MAX_QUESTION:
        raise ValueError("La pregunta debe tener entre 1 y 500 caracteres.")
    if any(ord(c) < 32 and c not in "\n\t" for c in question):
        raise ValueError("La pregunta contiene caracteres no válidos.")
    return question.strip()


ACTIVE = frozenset({Status.PENDING, Status.CLAIMED, Status.ANALYZING})


class Rejected(Exception):
    def __init__(self, code: str):
        self.code = code


class RepositoryUnavailable(Exception):
    pass


class ImageUnavailable(Exception):
    pass


@dataclass(frozen=True)
class Observation:
    id: str
    robot: str
    owner: str
    expires_at: datetime
    status: Status = Status.PENDING
    digest: str | None = None
    description: str | None = None
    error: str | None = None

    question: str = DEFAULT_QUESTION
    parent_id: str | None = None
    history: list[dict[str, str]] = field(default_factory=list)
    target: str | None = None
    match: str | None = None

    def current(self, now: datetime) -> Observation:
        if self.status in ACTIVE and self.expires_at <= now:
            return replace(self, status=Status.EXPIRED, error="capture_timeout")
        return self


@dataclass
class RobotState:
    last_seen: datetime | None = None
    observations: dict[str, Observation] = field(default_factory=dict)

    def active(self, now: datetime) -> Observation | None:
        for job in self.observations.values():
            current = job.current(now)
            if current.status in ACTIVE:
                return current
        return None


T = TypeVar("T")


class ObservationRepository(Protocol):
    def transact(self, robot: str, operation: Callable[[RobotState], T]) -> T: ...


class ImageStore(Protocol):
    def put(self, observation: str, jpeg: bytes) -> None: ...
    def get(self, observation: str) -> bytes | None: ...
    def delete(self, observation: str) -> None: ...


@dataclass(frozen=True)
class VisualFinding:
    description: str
    match: str  # FOUND, NOT_FOUND, UNCERTAIN; never a movement instruction


class SceneDescriber(Protocol):
    def describe(self, jpeg: bytes, question: str = DEFAULT_QUESTION, history=()) -> str: ...
    def locate(self, jpeg: bytes, target: str, question: str, history=()) -> VisualFinding: ...


@dataclass
class DescribeScene:
    repository: ObservationRepository
    images: ImageStore
    describer: SceneDescriber
    now: Callable[[], datetime]
    ttl: timedelta = timedelta(seconds=30)
    online_window: timedelta = timedelta(seconds=10)
    demo_record_limit: int = 16

    def discard_image(self, job_id: str) -> None:
        try:
            self.images.delete(job_id)
        except ImageUnavailable:
            # Cloud Storage lifecycle is the backup cleanup mechanism.
            logger.warning("observation_image_cleanup_failed id=%s", job_id)

    def status(self, robot: str) -> tuple[bool, datetime | None, Status | None]:
        def op(state: RobotState):
            now = self.now()
            active = state.active(now)
            return (
                state.last_seen is not None and now - state.last_seen <= self.online_window,
                state.last_seen,
                active.status if active else None,
            )
        return self.repository.transact(robot, op)

    def request(self, robot: str, owner: str, question: str = DEFAULT_QUESTION,
                parent_id: str | None = None, target: str | None = None) -> Observation:
        question = validate_question(question)
        if target is not None:
            target = validate_question(target)
            if len(target) > 80:
                raise ValueError("Objetivo demasiado largo.")
        def op(state: RobotState):
            now = self.now()
            if state.active(now):
                raise Rejected("capture_already_active")
            history = []
            if parent_id is not None:
                parent = state.observations.get(parent_id)
                if parent is None or parent.owner != owner:
                    raise Rejected("observation_not_found")
                if parent.status != Status.READY or not parent.description:
                    raise Rejected("context_not_ready")
                history = (parent.history + [{"question": parent.question, "answer": parent.description}])[-3:]
            removed = None
            if len(state.observations) >= self.demo_record_limit:
                removed = next(iter(state.observations))
                del state.observations[removed]
            job = Observation(uuid4().hex, robot, owner, now + self.ttl, question=question, parent_id=parent_id,
                              history=history, target=target)
            state.observations[job.id] = job
            return job, removed
        job, removed = self.repository.transact(robot, op)
        if removed:
            self.discard_image(removed)
        return job

    def poll(self, robot: str, owner: str | None = None) -> Observation | None:
        def op(state: RobotState):
            now = self.now()
            state.last_seen = now
            job = state.active(now)
            if job is None or job.status != Status.PENDING or (owner is not None and job.owner != owner):
                return None
            claimed = replace(job, status=Status.CLAIMED)
            state.observations[job.id] = claimed
            return claimed
        return self.repository.transact(robot, op)

    def get(self, robot: str, owner: str, job_id: str) -> Observation:
        def op(state: RobotState):
            job = state.observations.get(job_id)
            if job is None or job.owner != owner:
                raise Rejected("observation_not_found")
            return job.current(self.now())
        return self.repository.transact(robot, op)

    def image(self, robot: str, owner: str, job_id: str) -> bytes:
        job = self.get(robot, owner, job_id)
        if job.status != Status.READY:
            raise Rejected("image_not_ready")
        try:
            jpeg = self.images.get(job_id)
        except ImageUnavailable as exc:
            raise Rejected("image_unavailable") from exc
        if jpeg is None:
            raise Rejected("image_not_found")
        return jpeg

    def receive(self, robot: str, job_id: str, jpeg: bytes, owner: str | None = None) -> Observation:
        digest = sha256(jpeg).hexdigest()

        def claim_analysis(state: RobotState):
            job = state.observations.get(job_id)
            if job is None or (owner is not None and job.owner != owner):
                raise Rejected("observation_not_found")
            job = job.current(self.now())
            if job.status == Status.EXPIRED:
                raise Rejected("capture_timeout")
            if job.digest == digest:
                return job, False
            if job.status != Status.CLAIMED:
                raise Rejected("capture_not_claimed")
            job = replace(job, status=Status.ANALYZING, digest=digest)
            state.observations[job_id] = job
            return job, True

        job, should_analyze = self.repository.transact(robot, claim_analysis)
        if not should_analyze:
            return job

        description = None
        match = None
        error = None
        try:
            self.images.put(job_id, jpeg)
            if job.target is not None:
                finding = self.describer.locate(jpeg, job.target, job.question, job.history)
                if finding.match not in ("FOUND", "NOT_FOUND", "UNCERTAIN"):
                    raise ValueError("Resultado visual no válido")
                description, match = finding.description.strip(), finding.match
            elif job.history:
                description = self.describer.describe(jpeg, job.question, history=job.history).strip()
            else:
                description = self.describer.describe(jpeg, job.question).strip()
            if not description:
                error = "description_unavailable"
        except ImageUnavailable:
            error = "image_unavailable"
        except Exception:
            error = "description_unavailable"

        def complete(state: RobotState):
            stored = state.observations.get(job_id)
            current = (stored or job).current(self.now())
            if stored is None or current.status == Status.EXPIRED:
                return replace(current, status=Status.EXPIRED, error="capture_timeout")
            result = replace(
                current,
                status=Status.ERROR if error else Status.READY,
                description=None if error else description,
                match=None if error else match,
                error=error,
            )
            state.observations[job_id] = result
            return result

        result = self.repository.transact(robot, complete)
        if result.status != Status.READY:
            self.discard_image(job_id)
        return result

