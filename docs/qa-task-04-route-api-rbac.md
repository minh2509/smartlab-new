# Task 4 — Phân quyền route và API

Ngày kiểm tra: 22/09/2026. Nhánh: `fix/hiepnt-smartlab-qa-completion`.

## Thay đổi

- Route thiếu quyền hiển thị trạng thái 403 với nút về trang chủ. Không chuyển về `/profile`, tránh vòng chuyển hướng khi chính `PROFILE_READ` bị thu hồi.
- Menu hồ sơ và route hồ sơ sử dụng cùng policy. `/my-posts` yêu cầu đăng nhập ngay tại route.
- Xóa thông báo yêu cầu `notifications.mark_read_own`, cùng nhóm thao tác thay đổi thông báo cá nhân. UI chỉ hiện nút xóa khi có quyền này; quyền sở hữu vẫn do service kiểm tra.
- Người được cấp `TASK_MANAGE` phải còn là thành viên ACTIVE của đúng dự án trước khi quản lý task hoặc dùng luồng nộp bài dành cho người quản lý. Test kiểm tra cả từ chối và dữ liệu không bị thay đổi.
- Giữ nghiệp vụ có sẵn: Admin quản lý task toàn cục; Leader ACTIVE của một dự án quản lý task trong dự án đó. Role Leader toàn hệ thống không tự cấp quyền trên dự án khác.
- Giữ quyền đọc sự kiện LAB/PUBLIC cho tài khoản đăng nhập. Sự kiện PROJECT yêu cầu quyền đọc và membership tại service; không dùng `PROJECT_READ` để chặn toàn bộ trang sự kiện.

## Kiểm chứng tự động

Backend: 135 test qua, không lỗi, thuộc các lớp sau:

`AdminAccountControllerSecurityTest`, `AdminRolePermissionControllerSecurityTest`,
`EventControllerSecurityTest`, `EventServiceImplTest`, `NotificationControllerTest`, `NotificationServiceImplTest`,
`MemberProfileResearchFieldSecurityTest`, `ProjectControllerSecurityTest`,
`TaskFileWorkflowControllerSecurityTest`, `TaskServiceImplTest`, `FileControllerSecurityTest`,
`LabAchievementControllerSecurityTest`, `LabArticleControllerSecurityTest`,
`LabNewsArticleControllerSecurityTest`.

Frontend: `npm run lint`, `npm run build` thành công; 8/8 test trong `e2e/route-rbac.spec.ts` qua.
Đã xem ảnh trang 403 trên desktop 1280px và mobile 390px; dùng màu, nút và typography hiện có.
Các test trình duyệt dùng profile/API giả lập để kiểm tra route, menu và trạng thái UI;
không thay thế kiểm thử tích hợp bằng tài khoản thật trên database đã restore.

## Checklist retest tích hợp

1. Đăng nhập lần lượt Admin, Leader, Member. Kiểm tra menu và nhập trực tiếp URL quản trị tài khoản, RBAC, thành viên, nội dung. Gọi API tương ứng để xác nhận từ chối ở server.
2. Thu hồi `PROFILE_READ` và `PROJECT_READ`, đăng nhập lại/refresh theo cơ chế vô hiệu phiên hiện có. Kiểm tra menu, route 403 và không có vòng chuyển hướng; sự kiện LAB/PUBLIC vẫn đọc được.
3. Với người có `TASK_MANAGE`, thử quản lý task trong dự án đang tham gia và một dự án khác. Thử lại sau khi membership bị gỡ/ngừng hoạt động. Yêu cầu bị chặn không được sửa task, phân công hay phát thông báo.
4. Leader ACTIVE quản lý được task của dự án mình phụ trách. Member thường chỉ nộp task được giao khi vẫn còn membership ACTIVE.
5. Thu hồi `notifications.mark_read_own` trong khi giữ `notifications.read_own`: vẫn xem được thông báo nhưng không có nút sửa/xóa; gọi trực tiếp API sửa/xóa phải bị từ chối. Người có quyền cũng không được thao tác thông báo của người khác.
6. Đăng xuất và mở `/my-posts`: phải về đăng nhập. Kiểm tra trang 403 trên desktop/mobile và nút về trang chủ.

Không sửa file trong `frontend/src/features/projects`. Chưa đánh dấu retest thủ công hoặc QA chéo hoàn thành.
