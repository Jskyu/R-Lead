"""S3-compatible storage service (MinIO)."""

import asyncio
import uuid
from datetime import datetime, timedelta
from io import BytesIO
from typing import Any

import boto3
from botocore.client import Config
from botocore.exceptions import ClientError

from app.config import get_settings

settings = get_settings()


class StorageService:
    """S3 호환 저장소 서비스."""

    def __init__(self) -> None:
        self.bucket_name = settings.s3_bucket_name

    def _get_client(self, endpoint_url: str | None = None) -> Any:
        """S3 클라이언트를 생성합니다 (MinIO 등 S3 호환 저장소: path-style)."""
        return boto3.client(
            "s3",
            endpoint_url=endpoint_url or settings.s3_endpoint,
            aws_access_key_id=settings.s3_access_key,
            aws_secret_access_key=settings.s3_secret_key,
            region_name=settings.s3_region,
            config=Config(signature_version="s3v4", s3={"addressing_style": "path"}),
        )

    async def ensure_bucket_exists(self) -> None:
        """버킷이 존재하는지 확인하고, 없으면 생성합니다."""

        def _ensure_bucket() -> Any:
            s3_client = self._get_client()
            try:
                # 버킷 존재 확인
                s3_client.head_bucket(Bucket=self.bucket_name)
            except ClientError as e:
                error_code = e.response.get("Error", {}).get("Code", "")
                if error_code == "404":
                    # 버킷이 없으면 생성
                    try:
                        s3_client.create_bucket(Bucket=self.bucket_name)
                        print(f"✓ MinIO bucket '{self.bucket_name}' created successfully")
                    except ClientError as create_error:
                        print(f"Failed to create bucket: {create_error}")
                        raise
                else:
                    raise

        await asyncio.to_thread(_ensure_bucket)

    async def upload_image(
        self, image_data: bytes, content_type: str = "image/jpeg"
    ) -> tuple[str, str]:
        """
        이미지를 S3에 업로드합니다.

        Args:
            image_data: 이미지 바이트 데이터
            content_type: 이미지 MIME 타입

        Returns:
            (s3_key, s3_url) 튜플
        """
        # 고유한 파일명 생성
        file_id = uuid.uuid4()
        timestamp = datetime.now().strftime("%Y%m%d")
        s3_key = f"cards/{timestamp}/{file_id}.jpg"

        def _upload() -> Any:
            s3_client = self._get_client()
            try:
                s3_client.put_object(
                    Bucket=self.bucket_name,
                    Key=s3_key,
                    Body=BytesIO(image_data),
                    ContentType=content_type,
                )

                # S3 URL 생성 (임시 서명된 URL은 필요할 때 별도 생성)
                s3_url = f"{settings.s3_endpoint}/{self.bucket_name}/{s3_key}"

                return s3_key, s3_url

            except ClientError as e:
                raise Exception(f"S3 upload failed: {str(e)}") from e

        return await asyncio.to_thread(_upload)

    async def generate_presigned_url(self, s3_key: str, expiration: int = 3600) -> str:
        """
        임시 서명된 URL을 생성합니다.

        Args:
            s3_key: S3 객체 키
            expiration: 만료 시간 (초)

        Returns:
            임시 서명된 URL
        """

        def _generate_url() -> Any:
            # 서명은 호스트를 포함하므로, 브라우저가 접근할 공개 주소로 직접 서명한다.
            # (내부 주소로 서명한 뒤 문자열 치환하면 SignatureDoesNotMatch 가 난다)
            s3_client = self._get_client(settings.s3_public_endpoint or settings.s3_endpoint)
            try:
                return s3_client.generate_presigned_url(
                    "get_object",
                    Params={"Bucket": self.bucket_name, "Key": s3_key},
                    ExpiresIn=expiration,
                )
            except ClientError as e:
                raise Exception(f"Presigned URL generation failed: {str(e)}") from e

        return await asyncio.to_thread(_generate_url)

    async def download_image(self, s3_key: str) -> bytes:
        """
        S3에서 이미지를 다운로드합니다.

        Args:
            s3_key: S3 객체 키

        Returns:
            이미지 바이트 데이터
        """

        def _download() -> Any:
            s3_client = self._get_client()
            try:
                response = s3_client.get_object(Bucket=self.bucket_name, Key=s3_key)
                return response["Body"].read()
            except ClientError as e:
                raise Exception(f"S3 download failed: {str(e)}") from e

        return await asyncio.to_thread(_download)

    async def delete_image(self, s3_key: str) -> None:
        """
        S3에서 이미지를 삭제합니다.

        Args:
            s3_key: S3 객체 키
        """

        def _delete() -> Any:
            s3_client = self._get_client()
            try:
                s3_client.delete_object(Bucket=self.bucket_name, Key=s3_key)
            except ClientError as e:
                raise Exception(f"S3 delete failed: {str(e)}") from e

        await asyncio.to_thread(_delete)

    def calculate_delete_date(self) -> datetime:
        """이미지 삭제 예정일을 계산합니다."""
        return datetime.now() + timedelta(days=settings.image_retention_days)


# Singleton instance
storage_service = StorageService()
