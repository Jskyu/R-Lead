"""Image processing utilities."""

from io import BytesIO

from PIL import Image, ImageOps


def resize_image_for_ai(
    image_data: bytes,
    max_width: int = 1024,
    max_height: int = 1024,
    quality: int = 85,
) -> bytes:
    """
    AI 분석용으로 이미지를 압축합니다.

    원본 이미지는 S3에 그대로 보관하고,
    AI에는 작은 사이즈로 압축된 이미지를 전송하여 비용/속도를 최적화합니다.
    이미 작은 이미지는 키우지 않습니다.

    Args:
        image_data: 원본 이미지 바이트 데이터
        max_width: 최대 너비
        max_height: 최대 높이
        quality: JPEG 품질

    Returns:
        압축된 이미지 바이트 데이터
    """
    with Image.open(BytesIO(image_data)) as opened:
        # EXIF 방향 정보 적용 (휴대폰 사진이 누워 있는 문제 방지)
        image = ImageOps.exif_transpose(opened) or opened

        # RGB 모드로 변환 (RGBA, P 등 다른 모드 처리)
        if image.mode != "RGB":
            image = image.convert("RGB")

        # 비율 유지하면서 리사이징
        image.thumbnail((max_width, max_height), Image.Resampling.LANCZOS)

        output = BytesIO()
        image.save(output, format="JPEG", quality=quality, optimize=True)
        return output.getvalue()
