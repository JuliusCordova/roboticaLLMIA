"""Rules and use cases. No dependencies on FastAPI, Firebase or Google Cloud."""
from __future__ import annotations

from dataclasses import dataclass, field, replace
from datetime import datetime, timedelta
from enum import StrEnum
from hashlib import sha256
from typing import Callable, Protocol, TypeVar
from uuid import uuid4


class Status(StrEnum):
    PENDING = "PENDIENTE"
    CLAIMED = "RECLAMADA"
    ANALYZING = "ANALIZANDO"
    READY = "LISTA"
    ERROR = "ERROR"
    EXPIRED = "VENCIDA"


ACTIVE = frozenset({Status.PENDING, Status.CLAIMED, Status.ANALYZING})


class Rejected(Exception):
    def __init__(self, code: str):
        self.code = code


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


class SceneDescriber(Protocol):
    def describe(self, jpeg: bytes) -> str: ...


@dataclass
class DescribeScene:
    repository: ObservationRepository
    images: ImageStore
    describer: SceneDescriber
    now: Callable[[], datetime]
    ttl: timedelta = timedelta(seconds=30)
    online_window: timedelta = timedelta(seconds=10)
    demo_record_limit: int = 16

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

    def request(self, robot: str, owner: str) -> Observation:
        def op(state: RobotState):
            now = self.now()
            if state.active(now):
                raise Rejected("capture_already_active")
            removed = None
            if len(state.observations) >= self.demo_record_limit:
                removed = next(iter(state.observations))
                del state.observations[removed]
            job = Observation(uuid4().hex, robot, owner, now + self.ttl)
            state.observations[job.id] = job
            return job, removed
        job, removed = self.repository.transact(robot, op)
        if removed:
            self.images.delete(removed)
        return job

    def poll(self, robot: str) -> Observation | None:
        def op(state: RobotState):
            now = self.now()
            state.last_seen = now
            job = state.active(now)
            if job is None or job.status != Status.PENDING:
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
        jpeg = self.images.get(job_id)
        if jpeg is None:
            raise Rejected("image_not_found")
        return jpeg

    def receive(self, robot: str, job_id: str, jpeg: bytes) -> Observation:
        digest = sha256(jpeg).hexdigest()

        def claim_analysis(state: RobotState):
            job = state.observations.get(job_id)
            if job is None:
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
        error = None
        try:
            self.images.put(job_id, jpeg)
            description = self.describer.describe(jpeg).strip()
            if not description:
                error = "description_unavailable"
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
                error=error,
            )
            state.observations[job_id] = result
            return result

        result = self.repository.transact(robot, complete)
        if result.status != Status.READY:
            self.images.delete(job_id)
        return result
