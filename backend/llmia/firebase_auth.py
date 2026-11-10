"""Verify Google Firebase user tokens. The device has its own credential."""

from typing import Callable


class InvalidCredential(Exception):
    pass


class AuthUnavailable(Exception):
    pass


def firebase_verifier(project_id: str, allowed_uids: frozenset[str]) -> Callable:
    from firebase_admin import auth, exceptions, initialize_app
    from google.auth.exceptions import GoogleAuthError

    app = initialize_app(options={"projectId": project_id}, name=f"llmia-{project_id}")

    def verify(token: str) -> tuple[str, bool]:
        try:
            claims = auth.verify_id_token(token, app=app, check_revoked=True)
        except (auth.CertificateFetchError, GoogleAuthError) as exc:
            raise AuthUnavailable from exc
        except (auth.InvalidIdTokenError, auth.ExpiredIdTokenError, auth.RevokedIdTokenError,
                auth.UserDisabledError, auth.UserNotFoundError, ValueError) as exc:
            raise InvalidCredential from exc
        except exceptions.FirebaseError as exc:
            raise AuthUnavailable from exc
        uid = claims["uid"]
        return uid, uid in allowed_uids

    return verify
