"""Google Cloud adapters for the observation ports."""
from dataclasses import asdict, replace

from google.api_core.exceptions import GoogleAPICallError, NotFound, RetryError
from google.auth.exceptions import GoogleAuthError
from google.cloud import firestore, storage

from .core import ImageUnavailable, Observation, RepositoryUnavailable, RobotState, Status


class FirestoreObservations:
    """One bounded demo aggregate per robot; all transitions are transactional."""

    def __init__(self, client):
        self.client = client
        self.collection = client.collection("llmia_robots")

    @staticmethod
    def decode(data):
        if not data:
            return RobotState()
        # An array preserves insertion order for pruning the oldest observation.
        observations = [
            Observation(**{**record, "status": Status(record["status"])})
            for record in data.get("observations", [])
        ]
        return RobotState(data.get("last_seen"), {obs.id: obs for obs in observations})

    @staticmethod
    def encode(state):
        return {
            "last_seen": state.last_seen,
            "observations": [
                {**asdict(obs), "status": obs.status.value}
                for obs in state.observations.values()
            ],
        }

    def transact(self, robot, operation):
        ref = self.collection.document(robot)
        transaction = self.client.transaction(max_attempts=5)

        @firestore.transactional
        def apply(tx):
            snapshot = ref.get(transaction=tx, timeout=5, retry=None)
            previous = self.decode(snapshot.to_dict())
            state = replace(previous, observations=previous.observations.copy())
            result = operation(state)
            if state != previous:
                tx.set(ref, self.encode(state))
            return result

        try:
            return apply(transaction)
        except (GoogleAPICallError, GoogleAuthError, RetryError, ValueError) as exc:
            raise RepositoryUnavailable from exc


class StorageImages:
    def __init__(self, bucket):
        self.bucket = bucket

    def blob(self, observation):
        return self.bucket.blob(f"observations/{observation}.jpg")

    def put(self, observation, jpeg):
        try:
            self.blob(observation).upload_from_string(
                jpeg, content_type="image/jpeg", if_generation_match=0, timeout=8, retry=None,
            )
        except (GoogleAPICallError, GoogleAuthError, RetryError) as exc:
            raise ImageUnavailable from exc

    def get(self, observation):
        try:
            return self.blob(observation).download_as_bytes(timeout=5, retry=None)
        except NotFound:
            return None
        except (GoogleAPICallError, GoogleAuthError, RetryError) as exc:
            raise ImageUnavailable from exc

    def delete(self, observation):
        try:
            self.blob(observation).delete(timeout=3, retry=None)
        except NotFound:
            return
        except (GoogleAPICallError, GoogleAuthError, RetryError) as exc:
            raise ImageUnavailable from exc


def cloud_adapters(project, database, bucket):
    db = firestore.Client(project=project, database=database)
    images = storage.Client(project=project).bucket(bucket)
    return FirestoreObservations(db), StorageImages(images)
