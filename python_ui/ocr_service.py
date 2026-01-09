from io import BytesIO
from PIL import Image


MAX_DIMENSION = 1280
JPEG_QUALITY = 70


class OcrService:
    def __init__(self, backend_client):
        self._backend = backend_client

    def recognize(self, image):
        payload = self._encode_image(image)
        return self._backend.solve_with_image(
            payload,
            "Please analyze this image and answer any questions shown in it. Provide a concise, correct answer.",
        )

    def recognize_stream(self, image, chunk_consumer):
        payload = self._encode_image(image)
        return self._backend.solve_with_image_stream(
            payload,
            "Please analyze this image and answer any questions shown in it. Provide a concise, correct answer.",
            chunk_consumer,
        )

    def _encode_image(self, image):
        normalized = self._normalize_image(image)
        scaled = self._scale_down(normalized)
        out = BytesIO()
        scaled.save(out, format="JPEG", quality=JPEG_QUALITY, optimize=True)
        return out.getvalue()

    def _normalize_image(self, image):
        if image.mode == "RGB":
            return image
        return image.convert("RGB")

    def _scale_down(self, image):
        width, height = image.size
        max_dim = max(width, height)
        if max_dim <= MAX_DIMENSION:
            return image
        scale = MAX_DIMENSION / float(max_dim)
        new_size = (max(1, int(round(width * scale))), max(1, int(round(height * scale))))
        return image.resize(new_size, Image.BILINEAR)
