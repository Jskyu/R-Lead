import imageCompression from 'browser-image-compression'

/**
 * 이미지를 리사이징하고 EXIF 방향을 적용합니다.
 * @param file 원본 이미지 파일
 * @param maxSizeMB 최대 파일 크기 (MB)
 * @param maxWidthOrHeight 긴 변의 최대 픽셀 수
 * @returns 압축된 이미지 파일
 */
export async function resizeAndFixOrientation(
  file: File,
  maxSizeMB: number = 2,
  maxWidthOrHeight: number = 1600
): Promise<File> {
  const options = {
    maxSizeMB,
    maxWidthOrHeight,
    useWebWorker: true,
    // EXIF 방향 자동 수정
    exifOrientation: undefined, // browser-image-compression이 자동으로 처리
  }

  try {
    const compressedFile = await imageCompression(file, options)
    return compressedFile
  } catch (error) {
    console.error('Image compression error:', error)
    throw new Error('이미지 처리 중 오류가 발생했습니다.')
  }
}

/**
 * UUID v4 생성
 */
export function generateUUID(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}
