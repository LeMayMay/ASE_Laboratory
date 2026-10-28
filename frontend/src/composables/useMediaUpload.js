import { ref, computed } from 'vue'
import { uploadMedia } from '../api'
import { isLocalMediaUrl } from '../utils/mediaUtils'

/**
 * Composable providing reusable media upload lifecycle management.
 * Follows the Vue 3 Composition Pattern to decouple upload state from UI views.
 */
export function useMediaUpload(onMediaUploaded) {
  const fileInputRef = ref(null)
  const uploading = ref(false)
  const uploadError = ref('')
  const isUploadedMedia = ref(false)

  const isMediaReadonly = (mediaUrl) => {
    return isUploadedMedia.value || (Boolean(mediaUrl) && isLocalMediaUrl(mediaUrl))
  }

  async function handleFileChange(event) {
    const file = event.target.files?.[0]
    if (!file) return

    uploading.value = true
    uploadError.value = ''
    try {
      const res = await uploadMedia(file)
      isUploadedMedia.value = true
      if (onMediaUploaded) {
        onMediaUploaded(res.mediaUrl)
      }
      return res.mediaUrl
    } catch (e) {
      uploadError.value = e.response?.data?.detail || e.response?.data?.message || 'Failed to upload media'
      throw e
    } finally {
      uploading.value = false
    }
  }

  function clearMedia(onCleared) {
    uploadError.value = ''
    isUploadedMedia.value = false
    if (fileInputRef.value) {
      fileInputRef.value.value = ''
    }
    if (onCleared) {
      onCleared()
    }
  }

  return {
    fileInputRef,
    uploading,
    uploadError,
    isUploadedMedia,
    isMediaReadonly,
    handleFileChange,
    clearMedia
  }
}
