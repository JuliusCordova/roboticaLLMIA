"""Single-process adapters for the simulator. Do not use as cloud storage."""
from dataclasses import replace
from threading import Lock

from .core import RobotState


class MemoryObservations:
    def __init__(self):
        self.states: dict[str, RobotState] = {}
        self.lock = Lock()

    def transact(self, robot, operation):
        with self.lock:
            previous = self.states.get(robot, RobotState())
            state = replace(previous, observations=previous.observations.copy())
            result = operation(state)
            self.states[robot] = state
            return result


class MemoryImages:
    def __init__(self):
        self.images: dict[str, bytes] = {}
        self.lock = Lock()

    def put(self, observation, jpeg):
        with self.lock:
            self.images[observation] = jpeg

    def get(self, observation):
        with self.lock:
            return self.images.get(observation)

    def delete(self, observation):
        with self.lock:
            self.images.pop(observation, None)


class SimulatedDescriber:
    """Distinguishes the solid-color test scenes; does not recognize objects."""

    def describe(self, jpeg: bytes) -> str:
        from io import BytesIO
        from PIL import Image

        with Image.open(BytesIO(jpeg)) as picture:
            pixel = picture.convert("RGB").resize((1, 1)).getpixel((0, 0))
        red, green, blue = pixel
        if red > max(green, blue) + 50:
            return "[Simulación] La imagen de prueba tiene un fondo rojo."
        if blue > max(red, green) + 50:
            return "[Simulación] La imagen de prueba tiene un fondo azul."
        return "[Simulación] Se recibió una imagen sin un color de prueba dominante."
