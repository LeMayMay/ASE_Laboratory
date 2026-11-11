<template>
  <div class="app-shell">
    <header class="app-header">
      <h1>Instakill</h1>
      <nav v-if="user">
        <div class="view-switcher">
          <button
            type="button"
            :class="{ active: activeView === 'feed' }"
            @click="switchView('feed')"
          >
            Feed
          </button>
          <button
            type="button"
            :class="{ active: activeView === 'analytics' }"
            @click="switchView('analytics')"
          >
            Analytics
          </button>
        </div>
        <span class="user">@{{ user.username }}</span>
        <button @click="logout">Logout</button>
      </nav>
    </header>

    <main class="content">
      <section v-if="!user" class="card">
        <div class="tabs">
          <button :class="{ active: authMode === 'login' }" @click="authMode = 'login'">Login</button>
          <button :class="{ active: authMode === 'register' }" @click="authMode = 'register'">Register</button>
        </div>
        <form @submit.prevent="submitAuth" class="form">
          <input v-model="authForm.usernameOrEmail" v-if="authMode === 'login'" placeholder="Username or email" required>
          <input v-model="authForm.username" v-else placeholder="Username" required minlength="3">
          <input v-model="authForm.email" v-if="authMode === 'register'" type="email" placeholder="Email" required>
          <input v-model="authForm.password" type="password" placeholder="Password" required minlength="6">
          <p v-if="error" class="error">{{ error }}</p>
          <button type="submit">Continue</button>
        </form>
      </section>

      <!-- Analytics View -->
      <section v-else-if="activeView === 'analytics'" class="analytics-section">
        <div class="feed-toolbar">
          <h2>Analytics for @{{ user.username }}</h2>
          <button type="button" class="refresh-btn" @click="loadAnalytics" :disabled="analyticsLoading">
            {{ analyticsLoading ? 'Refreshing...' : 'Refresh' }}
          </button>
        </div>

        <p v-if="analyticsError" class="error card">{{ analyticsError }}</p>

        <div v-if="analytics" class="analytics-grid">
          <!-- 5. Engagement Percentile Rank -->
          <div class="card analytics-card highlight-card">
            <h3>Engagement Standing</h3>
            <p class="rank-headline">
              Your posts rank in the
              <strong>top {{ formatNumber(analytics.engagementRank.topPercentage, 1) }}%</strong>
              of users by average engagement.
            </p>
            <div class="stat-pills">
              <span class="pill">
                Rank #{{ analytics.engagementRank.rank }} of {{ analytics.engagementRank.totalUsers }} users
              </span>
              <span class="pill">
                {{ formatNumber(analytics.engagementRank.averageEngagement, 2) }} avg (likes + comments) / post
              </span>
            </div>
          </div>

          <!-- 3. Like-to-Comment Ratio -->
          <div class="card analytics-card">
            <h3>Like-to-Comment Ratio</h3>
            <div class="big-metric">
              {{ formatNumber(analytics.likeCommentRatio.likeToCommentRatio, 2) }}
            </div>
            <p class="muted">
              {{ analytics.likeCommentRatio.totalLikes }} total likes :
              {{ analytics.likeCommentRatio.totalComments }} total comments (all posts combined)
            </p>
          </div>

          <!-- 4. Average Likes per Post by Media Type (Photos vs Videos) -->
          <div class="card analytics-card">
            <h3>Average Likes per Post by Media Type</h3>
            <div class="media-comparison">
              <div
                v-for="item in analytics.averageLikesByMediaType"
                :key="item.mediaType"
                class="media-stat-box"
              >
                <span class="media-label">{{ item.mediaType === 'VIDEO' ? 'Videos' : 'Photos' }}</span>
                <strong class="media-avg">{{ formatNumber(item.averageLikes, 2) }}</strong>
                <small class="muted">
                  avg likes/post ({{ item.totalLikes }} likes across {{ item.postCount }} posts)
                </small>
              </div>
            </div>
          </div>

          <!-- 1. Total Posts, Likes, and Comments per Week -->
          <div class="card analytics-card full-width">
            <h3>Weekly Activity (Grouped by Week of Creation)</h3>
            <table v-if="analytics.weeklyActivity && analytics.weeklyActivity.length" class="analytics-table">
              <thead>
                <tr>
                  <th>Week</th>
                  <th>Total Posts</th>
                  <th>Total Likes</th>
                  <th>Total Comments</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in analytics.weeklyActivity" :key="row.week">
                  <td><strong>{{ row.week }}</strong></td>
                  <td>{{ row.totalPosts }}</td>
                  <td>{{ row.totalLikes }}</td>
                  <td>{{ row.totalComments }}</td>
                </tr>
              </tbody>
            </table>
            <p v-else class="muted">No posts created yet.</p>
          </div>

          <!-- 2. Top 5 Posts by Engagement -->
          <div class="card analytics-card full-width">
            <h3>Top 5 Posts by Engagement (Likes + Comments)</h3>
            <div v-if="analytics.topPostsByEngagement && analytics.topPostsByEngagement.length" class="top-posts-list">
              <div
                v-for="(post, idx) in analytics.topPostsByEngagement"
                :key="post.postId"
                class="top-post-item"
              >
                <div class="top-post-rank">#{{ idx + 1 }}</div>
                <div class="top-post-body">
                  <p class="top-post-content">{{ post.content }}</p>
                  <small class="muted">
                    {{ formatDate(post.createdAt) }}
                    <span v-if="post.imageUrl">
                      · {{ isVideoUrl(post.imageUrl) ? 'Video' : 'Photo' }}
                    </span>
                  </small>
                </div>
                <div class="top-post-metrics">
                  <span class="engagement-badge">{{ post.engagement }} engagement</span>
                  <small class="muted">{{ post.likeCount }} likes · {{ post.commentCount }} comments</small>
                </div>
              </div>
            </div>
            <p v-else class="muted">No posts available to rank.</p>
          </div>
        </div>
      </section>

      <!-- Feed View -->
      <section v-else class="feed">
        <form @submit.prevent="createNewPost" class="card form composer">
          <textarea v-model="composer.content" rows="3" placeholder="Share something..." required></textarea>
          
          <div class="media-input-group">
            <label class="file-upload-btn">
              <span>{{ uploading ? 'Uploading...' : 'Choose File' }}</span>
              <input
                type="file"
                ref="fileInputRef"
                @change="handleFileChange"
                :disabled="uploading"
                accept="image/*,video/*"
              >
            </label>
            <input
              v-model="composer.imageUrl"
              :placeholder="isMediaUrlReadonly ? 'Uploaded media URL' : 'Or paste image/video URL (optional)'"
              :disabled="uploading"
              :readonly="isMediaUrlReadonly"
              :class="{ 'readonly-input': isMediaUrlReadonly }"
            >
            <button
              v-if="composer.imageUrl"
              type="button"
              class="clear-media-btn"
              @click="clearMedia"
            >
              Clear
            </button>
          </div>

          <p v-if="uploadError" class="error">{{ uploadError }}</p>

          <div v-if="composer.imageUrl" class="composer-media-preview">
            <video v-if="isVideoUrl(composer.imageUrl)" controls :src="composer.imageUrl"></video>
            <img v-else :src="composer.imageUrl" alt="Media preview">
          </div>

          <div class="actions">
            <button type="submit" :disabled="uploading">Post</button>
          </div>
        </form>

        <div class="feed-toolbar">
          <h2>Feed</h2>
          <div class="sort">
            <button :class="{ active: sort === 'new' }" @click="changeSort('new')">Newest</button>
            <button :class="{ active: sort === 'trending' }" @click="changeSort('trending')">Trending</button>
          </div>
        </div>

        <article v-for="post in posts" :key="post.id" class="card post">
          <header>
            <div>
              <h3>@{{ post.author.username }}</h3>
              <small>{{ formatDate(post.createdAt) }}</small>
            </div>
            <div class="metrics">
              <span>{{ post.likeCount }} likes</span>
              <span> | </span>
              <span>{{ post.commentCount }} comments</span>
            </div>
          </header>
          <p>{{ post.content }}</p>
          <div v-if="post.imageUrl" class="post-media">
            <video v-if="isVideoUrl(post.imageUrl)" controls :src="post.imageUrl"></video>
            <img v-else :src="post.imageUrl" alt="post image">
          </div>
          
          <!-- Comments Section -->
          <section class="comments">
            <div v-if="comments[post.id] && comments[post.id].length">
              <div v-for="comment in comments[post.id]" :key="comment.id" class="comment">
                <b>@{{ comment.author.username }}</b> <small>{{ formatDate(comment.createdAt) }}</small>
                <div>{{ comment.content }}</div>
              </div>
            </div>
            <div v-else class="no-comments">
              <em>No comments yet.</em>
            </div>
            <form v-if="user" @submit.prevent="submitComment(post.id)" class="comment-form">
              <input
                v-model="newComment[post.id]"
                :placeholder="'Add a comment...'"
                required
                minlength="1"
              />
              <button type="submit" :disabled="commentLoading[post.id]">Comment</button>
            </form>
            <div v-if="commentError[post.id]" class="error">{{ commentError[post.id] }}</div>
          </section>

          <footer>
            <button @click="togglePostLike(post)">
              {{ post.likedByCurrentUser ? 'Unlike' : 'Like' }}
            </button>
          </footer>
        </article>
      </section>
    </main>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, computed } from 'vue'
import { createPost, fetchMe, listFeed, login, register as registerUser, toggleLike, listComments, addComment, fetchAnalytics } from './api'
import { isVideoUrl } from './utils/mediaUtils'
import { useMediaUpload } from './composables/useMediaUpload'

const user = ref(null)
const posts = ref([])
const sort = ref('new')
const activeView = ref('feed')
const authMode = ref('login')
const error = ref('')
const authForm = reactive({ username: '', email: '', password: '', usernameOrEmail: '' })
const composer = reactive({ content: '', imageUrl: '' })

// Analytics state
const analytics = ref(null)
const analyticsLoading = ref(false)
const analyticsError = ref('')

// Media upload composable (Composition Pattern)
const {
  fileInputRef,
  uploading,
  uploadError,
  isMediaReadonly,
  handleFileChange,
  clearMedia: resetMediaUpload
} = useMediaUpload((mediaUrl) => {
  composer.imageUrl = mediaUrl
})

const isMediaUrlReadonly = computed(() => isMediaReadonly(composer.imageUrl))

function clearMedia() {
  resetMediaUpload(() => {
    composer.imageUrl = ''
  })
}

// Comments state
const comments = reactive({})
const newComment = reactive({})
const commentLoading = reactive({})
const commentError = reactive({})

onMounted(async () => {
  await bootstrap()
})

async function bootstrap() {
  try {
    user.value = await fetchMe()
    await loadFeed()
  } catch (e) {
    user.value = null
  }
}

async function switchView(view) {
  activeView.value = view
  if (view === 'analytics') {
    await loadAnalytics()
  } else {
    await loadFeed()
  }
}

async function loadAnalytics() {
  analyticsLoading.value = true
  analyticsError.value = ''
  try {
    analytics.value = await fetchAnalytics()
  } catch (e) {
    analyticsError.value = e.response?.data?.detail || 'Failed to load analytics'
  } finally {
    analyticsLoading.value = false
  }
}

async function submitAuth() {
  error.value = ''
  try {
    if (authMode.value === 'login') {
      const result = await login({
        usernameOrEmail: authForm.usernameOrEmail,
        password: authForm.password
      })
      user.value = result.user
    } else {
      const result = await registerUser({
        username: authForm.username,
        email: authForm.email,
        password: authForm.password
      })
      user.value = result.user
    }
    activeView.value = 'feed'
    await loadFeed()
  } catch (e) {
    error.value = e.response?.data?.detail || 'Authentication failed'
  }
}

async function loadFeed() {
  posts.value = await listFeed({ sort: sort.value })
  // Load comments for each post
  for (const post of posts.value) {
    await loadComments(post.id)
  }
}

async function loadComments(postId) {
  try {
    commentError[postId] = ''
    comments[postId] = await listComments(postId)
  } catch (e) {
    comments[postId] = []
    commentError[postId] = 'Failed to load comments'
  }
}

async function submitComment(postId) {
  if (!newComment[postId] || !newComment[postId].trim()) return
  commentLoading[postId] = true
  commentError[postId] = ''
  try {
    await addComment(postId, { content: newComment[postId] })
    newComment[postId] = ''
    await loadComments(postId)
    // Optionally, update commentCount in post
    const post = posts.value.find(p => p.id === postId)
    if (post) post.commentCount = (comments[postId]?.length || 0)
  } catch (e) {
    commentError[postId] = e.response?.data?.detail || 'Failed to add comment'
  } finally {
    commentLoading[postId] = false
  }
}

async function createNewPost() {
  if (!composer.content.trim()) return
  await createPost({ content: composer.content, imageUrl: composer.imageUrl || null })
  composer.content = ''
  clearMedia()
  await loadFeed()
}

async function togglePostLike(post) {
  const result = await toggleLike(post.id)
  post.likedByCurrentUser = result.liked
  post.likeCount = result.likeCount
}

function changeSort(next) {
  if (sort.value === next) return
  sort.value = next
  loadFeed()
}

function formatDate(iso) {
  return new Date(iso).toLocaleString()
}

function formatNumber(val, decimals = 2) {
  const num = Number(val ?? 0)
  return Number.isFinite(num) ? num.toFixed(decimals) : '0.00'
}

async function logout() {
  try {
    await fetch('/auth/logout', { method: 'POST', credentials: 'include' })
  } catch (e) {
    // ignore
  }
  document.cookie = 'ACCESS_TOKEN=; Max-Age=0; path=/'
  user.value = null
  posts.value = []
  analytics.value = null
  activeView.value = 'feed'
}
</script>

<style scoped>
.app-shell {
  max-width: 960px;
  margin: 0 auto;
  padding: 2rem 1rem;
}

.app-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
}

.app-header nav {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.app-header button {
  background: #111827;
  color: #fff;
  border: none;
  padding: 0.5rem 1rem;
  border-radius: 999px;
  cursor: pointer;
}

.view-switcher {
  display: flex;
  gap: 0.4rem;
  background: #e5e7eb;
  padding: 0.25rem;
  border-radius: 999px;
}

.view-switcher button {
  background: transparent;
  color: #374151;
  padding: 0.35rem 0.9rem;
  font-weight: 500;
}

.view-switcher button.active {
  background: #111827;
  color: #fff;
}

.content {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.card {
  background: #fff;
  border-radius: 16px;
  padding: 1.5rem;
  box-shadow: 0 10px 30px rgba(17, 24, 39, 0.1);
}

.form {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.form input,
.form textarea {
  width: 100%;
  padding: 0.75rem;
  border-radius: 10px;
  border: 1px solid #d1d5db;
}

.form button {
  align-self: flex-start;
  background: #2563eb;
  color: #fff;
  border: none;
  padding: 0.5rem 1.25rem;
  border-radius: 999px;
}

.tabs {
  display: flex;
  gap: 1rem;
  margin-bottom: 1rem;
}

.tabs button {
  background: transparent;
  border: none;
  font-weight: 600;
  color: #6b7280;
}

.tabs button.active {
  color: #111827;
}

.error {
  color: #ef4444;
}

.feed-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.sort button,
.refresh-btn {
  margin-left: 0.5rem;
  border: none;
  background: #e5e7eb;
  padding: 0.4rem 0.9rem;
  border-radius: 999px;
  cursor: pointer;
}

.sort button.active {
  background: #111827;
  color: #fff;
}

.analytics-section {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.analytics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 1.25rem;
}

.analytics-card h3 {
  margin-top: 0;
  margin-bottom: 0.75rem;
  font-size: 1rem;
  color: #374151;
}

.highlight-card {
  grid-column: 1 / -1;
  background: linear-gradient(135deg, #1e3a8a, #2563eb);
  color: #fff;
}

.highlight-card h3 {
  color: #bfdbfe;
}

.rank-headline {
  font-size: 1.25rem;
  margin: 0.25rem 0 1rem;
}

.stat-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.pill {
  background: rgba(255, 255, 255, 0.18);
  padding: 0.35rem 0.8rem;
  border-radius: 999px;
  font-size: 0.875rem;
}

.big-metric {
  font-size: 2.25rem;
  font-weight: 700;
  color: #111827;
  margin-bottom: 0.25rem;
}

.muted {
  color: #6b7280;
  margin: 0;
}

.media-comparison {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem;
}

.media-stat-box {
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 0.85rem;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.media-label {
  font-weight: 600;
  color: #374151;
  font-size: 0.875rem;
}

.media-avg {
  font-size: 1.5rem;
  color: #2563eb;
}

.full-width {
  grid-column: 1 / -1;
}

.analytics-table {
  width: 100%;
  border-collapse: collapse;
}

.analytics-table th,
.analytics-table td {
  text-align: left;
  padding: 0.65rem 0.75rem;
  border-bottom: 1px solid #e5e7eb;
}

.analytics-table th {
  font-size: 0.85rem;
  text-transform: uppercase;
  letter-spacing: 0.03em;
  color: #6b7280;
}

.top-posts-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.top-post-item {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0.75rem 1rem;
  background: #f9fafb;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
}

.top-post-rank {
  font-weight: 700;
  font-size: 1.1rem;
  color: #2563eb;
  min-width: 2rem;
}

.top-post-body {
  flex: 1;
}

.top-post-content {
  margin: 0 0 0.25rem;
  font-weight: 500;
}

.top-post-metrics {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.2rem;
}

.engagement-badge {
  background: #dbeafe;
  color: #1e40af;
  font-weight: 600;
  font-size: 0.85rem;
  padding: 0.25rem 0.65rem;
  border-radius: 999px;
}

.post header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
}

.media-input-group {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.file-upload-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #e5e7eb;
  color: #1f2937;
  padding: 0.65rem 1rem;
  border-radius: 10px;
  cursor: pointer;
  font-weight: 500;
  white-space: nowrap;
  font-size: 0.875rem;
  transition: background 0.2s;
}

.file-upload-btn:hover {
  background: #d1d5db;
}

.file-upload-btn input[type="file"] {
  display: none;
}

.clear-media-btn {
  background: #f3f4f6;
  color: #4b5563;
  border: 1px solid #d1d5db;
  padding: 0.5rem 0.85rem;
  border-radius: 999px;
  cursor: pointer;
}

.readonly-input {
  background-color: #f3f4f6 !important;
  color: #6b7280 !important;
  cursor: not-allowed;
  user-select: none;
}

.composer-media-preview,
.post-media {
  margin-top: 0.75rem;
  border-radius: 12px;
  overflow: hidden;
  background: #000;
  display: flex;
  justify-content: center;
  align-items: center;
}

.composer-media-preview img,
.composer-media-preview video,
.post-media img,
.post-media video {
  max-width: 100%;
  max-height: 480px;
  width: 100%;
  display: block;
  border-radius: 12px;
}

.post img,
.post video {
  margin-top: 0.75rem;
  width: 100%;
  border-radius: 12px;
}

.post footer {
  margin-top: 1rem;
}

.post footer button {
  background: #111827;
  border: none;
  color: #fff;
  padding: 0.4rem 1.2rem;
  border-radius: 999px;
}
.comments {
  margin-top: 1.2rem;
  padding-top: 1rem;
  border-top: 1px solid #e5e7eb;
}
.comment {
  margin-bottom: 0.7rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid #f3f4f6;
}
.no-comments {
  color: #6b7280;
  margin-bottom: 0.7rem;
}
.comment-form {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.5rem;
}
.comment-form input {
  flex: 1;
  padding: 0.5rem;
  border-radius: 8px;
  border: 1px solid #d1d5db;
}
.comment-form button {
  background: #2563eb;
  color: #fff;
  border: none;
  padding: 0.4rem 1.1rem;
  border-radius: 999px;
}
</style>
