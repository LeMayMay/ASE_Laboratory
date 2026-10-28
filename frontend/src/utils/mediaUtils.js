/**
 * Utility functions for media URL inspection.
 */

const VIDEO_EXTENSIONS = ['.mp4', '.mov', '.webm', '.mkv']

export function isVideoUrl(url) {
  if (!url || typeof url !== 'string') return false
  const clean = url.split('?')[0].toLowerCase()
  return VIDEO_EXTENSIONS.some(ext => clean.endsWith(ext))
}

export function isLocalMediaUrl(url) {
  if (!url || typeof url !== 'string') return false
  return url.startsWith('/media/')
}
