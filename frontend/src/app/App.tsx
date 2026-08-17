import { Navigate, Route, Routes } from 'react-router-dom'
import { AdminLayout } from './layouts/AdminLayout'
import { AuthLayout } from './layouts/AuthLayout'
import { PublicLayout } from './layouts/PublicLayout'
import { AcceptInvitePage } from '../features/auth/pages/AcceptInvitePage'
import { ForgotPasswordPage } from '../features/auth/pages/ForgotPasswordPage'
import { LoginPage } from '../features/auth/pages/LoginPage'
import { NewPasswordPage } from '../features/auth/pages/NewPasswordPage'
import { VerifyResetOtpPage } from '../features/auth/pages/VerifyResetOtpPage'
import { AdminAccountsPage } from '../features/admin/pages/AdminAccountsPage'
import { AdminRbacPage } from '../features/admin/pages/AdminRbacPage'
import { ProfilePage } from '../features/profile/pages/ProfilePage'
import { ResearchFieldsPage } from '../features/profile/pages/ResearchFieldsPage'
import { AdminMembersPage } from '../features/profile/pages/AdminMembersPage'
import { MyPostsPage } from '../features/posts/pages/PostListPage'
import { PostFeedPage } from '../features/posts/pages/PostFeedPage'
import { PostDetailPage } from '../features/posts/pages/PostDetailPage'
import { PostCreatePage } from '../features/posts/pages/PostCreatePage'
import { PostEditPage } from '../features/posts/pages/PostEditPage'
import { PostReviewQueuePage } from '../features/posts/pages/PostReviewQueuePage'
import { PostReviewDetailPage } from '../features/posts/pages/PostReviewDetailPage'
import { HomePage } from '../features/public/pages/HomePage'
import { StaticPublicPage } from '../features/public/pages/StaticPublicPage'
import { RequirePermissions } from './RequirePermissions'
import { FilesPage } from '../features/files/pages/FilesPage'
import { ProjectListPage } from '../features/projects/pages/ProjectListPage'
import { ProjectDetailPage } from '../features/projects/pages/ProjectDetailPage'
import { ProjectManagementPage } from '../features/projects/pages/ProjectManagementPage'
import { EventManagementPage } from '../features/events/pages/EventManagementPage'
import { MyEvaluationsPage } from '../features/evaluations/pages/MyEvaluationsPage'
import { TasksPage } from '../features/tasks/pages/TasksPage'
import { accessPolicies } from './accessPolicy'

export function App() {
  return (
    <Routes>
      <Route element={<PublicLayout />}>
        <Route index element={<HomePage />} />
        <Route path="/trang-chu" element={<Navigate to="/" replace />} />
        <Route
          path="/gioi-thieu"
          element={
            <StaticPublicPage
              kind="about"
              title="Về phòng Smart Lab"
              description="Smart Lab là phòng nghiên cứu trực thuộc Khoa Công nghệ thông tin, nơi sinh viên tham gia dự án nghiên cứu và sản phẩm thật, có người hướng dẫn và đánh giá định kỳ."
            />
          }
        />
        <Route
          path="/linh-vuc"
          element={
            <StaticPublicPage
              kind="fields"
              title="Lĩnh vực nghiên cứu"
              description="Ba hướng nghiên cứu chính của Smart Lab: AI, Robotics và Kỹ thuật phần mềm."
            />
          }
        />
        <Route path="/du-an" element={<ProjectListPage />} />
        <Route path="/du-an/:id" element={<ProjectDetailPage />} />
        <Route
          path="/thanh-vien"
          element={
            <StaticPublicPage
              kind="members"
              title="Thành viên"
              description="Đội ngũ leader và thành viên đang tham gia các nhóm nghiên cứu trong Lab."
            />
          }
        />
        <Route
          path="/bai-viet"
          element={
            <StaticPublicPage
              kind="blog"
              title="Bài viết"
              description="Thông báo, kết quả nghiên cứu, bài viết học thuật và chia sẻ kinh nghiệm từ các nhóm dự án."
            />
          }
        />
        <Route path="/blog" element={<Navigate to="/bai-viet" replace />} />
        <Route path="/posts" element={<PostFeedPage />} />
        <Route path="/my-posts" element={<MyPostsPage />} />
        <Route path="/posts/new" element={<RequirePermissions allOf={[]}><PostCreatePage /></RequirePermissions>} />
        <Route path="/posts/review-queue" element={<RequirePermissions allOf={['posts.review']}><PostReviewQueuePage /></RequirePermissions>} />
        <Route path="/posts/review-queue/:id" element={<RequirePermissions allOf={['posts.review']}><PostReviewDetailPage /></RequirePermissions>} />
        <Route path="/posts/:slug/edit" element={<RequirePermissions allOf={[]}><PostEditPage /></RequirePermissions>} />
        <Route path="/posts/:slug" element={<PostDetailPage />} />
        <Route
          path="/tai-lieu"
          element={
            <StaticPublicPage
              kind="documents"
              title="Tài liệu"
              description="Tài liệu vận hành, hướng dẫn thành viên và biểu mẫu dùng trong quá trình làm việc tại Smart Lab."
            />
          }
        />
        <Route
          path="/su-kien"
          element={
            <StaticPublicPage
              kind="events"
              title="Sự kiện"
              description="Workshop, demo, seminar và các buổi đánh giá tiến độ của Smart Lab."
            />
          }
        />
        <Route
          path="/thu-vien-anh"
          element={
            <StaticPublicPage
              kind="gallery"
              title="Thư viện ảnh"
              description="Hình ảnh hoạt động, workshop, demo dự án và sinh hoạt nội bộ của Lab."
            />
          }
        />
        <Route path="/tuyen-thanh-vien" element={<Navigate to="/lien-he" replace />} />
        <Route
          path="/lien-he"
          element={
            <StaticPublicPage
              kind="contact"
              title="Liên hệ"
              description="Thông tin liên hệ và form gửi lời nhắn cho Smart Lab."
            />
          }
        />
        <Route
          path="/tim-kiem"
          element={
            <StaticPublicPage
              kind="search"
              title="Tìm kiếm"
              description="Tìm nhanh dự án, bài viết và thành viên trong nội dung công khai."
            />
          }
        />
      </Route>

      <Route element={<AuthLayout />}>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/forgot-password/otp" element={<VerifyResetOtpPage />} />
        <Route path="/forgot-password/new-password" element={<NewPasswordPage />} />
        <Route path="/accept-invite" element={<AcceptInvitePage />} />
      </Route>

      <Route element={<AdminLayout />}>
        <Route path="/profile" element={<RequirePermissions allOf={['PROFILE_READ']}><ProfilePage /></RequirePermissions>} />
        <Route path="/my-evaluations" element={<MyEvaluationsPage />} />
        <Route path="/files" element={<RequirePermissions allOf={accessPolicies.files}><FilesPage /></RequirePermissions>} />
        <Route path="/admin/projects" element={<RequirePermissions allOf={accessPolicies.projects}><ProjectManagementPage /></RequirePermissions>} />
        <Route path="/admin/events" element={<EventManagementPage />} />
        <Route path="/admin/tasks" element={<RequirePermissions allOf={accessPolicies.tasks}><TasksPage /></RequirePermissions>} />
        <Route path="/admin/research-fields" element={<RequirePermissions allOf={accessPolicies.researchFields}><ResearchFieldsPage /></RequirePermissions>} />
        <Route path="/admin/members" element={<RequirePermissions allOf={accessPolicies.members}><AdminMembersPage /></RequirePermissions>} />
        <Route path="/admin/accounts" element={<RequirePermissions allOf={accessPolicies.accounts}><AdminAccountsPage /></RequirePermissions>} />
        <Route path="/admin/rbac" element={<RequirePermissions allOf={accessPolicies.rbac}><AdminRbacPage /></RequirePermissions>} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
