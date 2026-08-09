import aiResearchImage from '../../assets/fields/ai-research.webp'
import roboticsResearchImage from '../../assets/fields/robotics-research.webp'
import softwareEngineeringImage from '../../assets/fields/software-engineering.webp'

export const stats = [
  { value: '38', label: 'thành viên đang hoạt động' },
  { value: '12', label: 'dự án nghiên cứu & sản phẩm' },
  { value: '17', label: 'bài báo & báo cáo khoa học' },
  { value: '3', label: 'lĩnh vực nghiên cứu chính' },
]

export const aboutQuickFacts = [
  { label: 'Thành lập', value: '2023' },
  { label: 'Trực thuộc', value: 'Khoa Công nghệ thông tin' },
  { label: 'Địa điểm', value: 'Phòng A3-502, Hoà Lạc' },
  { label: 'Lĩnh vực', value: 'AI · Robotics · SE' },
  { label: 'Thành viên', value: '38 người đang hoạt động' },
]

export const coreValues = [
  {
    title: 'Nghiên cứu thật',
    color: 'var(--s1)',
    description: 'Mỗi thành viên làm việc trên bài toán có mục tiêu và kết quả kiểm chứng được, không phải bài tập trình diễn.',
  },
  {
    title: 'Hướng dẫn sát',
    color: 'var(--s2)',
    description: 'Mỗi nhóm dự án có leader và giảng viên đồng hành, giao nhiệm vụ rõ ràng và phản hồi thường xuyên.',
  },
  {
    title: 'Đánh giá minh bạch',
    color: 'var(--s3)',
    description: 'Tiến độ, đóng góp và kết quả đều được ghi nhận trên hệ thống, đánh giá theo tiêu chí công khai.',
  },
]

export const operatingSteps = [
  {
    title: 'Admin cấp tài khoản',
    description: 'Thành viên nhận tài khoản nội bộ qua invite, hoàn thiện hồ sơ chuyên môn và lĩnh vực nghiên cứu quan tâm.',
  },
  {
    title: 'Vào nhóm dự án',
    description: 'Leader xét hồ sơ và phân thành viên vào một nhóm dự án phù hợp với lĩnh vực, năng lực và nhu cầu hiện tại.',
  },
  {
    title: 'Nhận nhiệm vụ',
    description: 'Thành viên được giao nhiệm vụ cụ thể trên hệ thống, có thời hạn và tiêu chí hoàn thành rõ ràng.',
  },
  {
    title: 'Đánh giá định kỳ',
    description: 'Cuối mỗi giai đoạn có buổi bảo vệ tiến độ và đánh giá đóng góp theo tiêu chí do leader thiết lập.',
  },
]

export const fields = [
  {
    id: 'ai',
    name: 'Trí tuệ nhân tạo',
    color: 'var(--s1)',
    image: aiResearchImage,
    imageAlt: 'Minh họa nghiên cứu trí tuệ nhân tạo trong phòng lab',
    tags: ['Computer Vision', 'NLP', 'Deep Learning'],
    description: 'Thị giác máy tính, xử lý ngôn ngữ tự nhiên và học máy ứng dụng cho bài toán thực tế trong nước.',
  },
  {
    id: 'robotics',
    name: 'Robotics',
    color: 'var(--s2)',
    image: roboticsResearchImage,
    imageAlt: 'Minh họa cánh tay robot và cảm biến trong phòng lab',
    tags: ['Embedded', 'Control', 'ROS'],
    description: 'Hệ thống nhúng, điều khiển và tự hành, từ mô hình mô phỏng tới phần cứng chạy được.',
  },
  {
    id: 'se',
    name: 'Kỹ thuật phần mềm',
    color: 'var(--s3)',
    image: softwareEngineeringImage,
    imageAlt: 'Minh họa kiến trúc phần mềm và quy trình kiểm thử',
    tags: ['Kiến trúc', 'DevOps', 'Kiểm thử'],
    description: 'Kiến trúc hệ thống, chất lượng phần mềm và quy trình phát triển cho sản phẩm dùng được thật.',
  },
]

export const projects = [
  {
    title: 'Nhận diện biển báo giao thông Việt Nam',
    field: 'AI',
    type: 'Research',
    status: 'Đang thực hiện',
    badge: 'ok',
    progress: '72%',
    cover: 'ph-ai',
    members: ['VA', 'NH', 'TL', '+3'],
    description: 'Bộ dữ liệu biển báo nội địa và mô hình phát hiện thời gian thực chạy được trên thiết bị biên.',
  },
  {
    title: 'Cánh tay robot phân loại vật thể',
    field: 'Robotics',
    type: 'Production',
    status: 'Đang thực hiện',
    badge: 'ok',
    progress: '45%',
    cover: 'ph-robotics',
    members: ['TM', 'QD', 'HA', '+1'],
    description: 'Tay gắp 4 bậc tự do kết hợp camera, phân loại và sắp xếp vật thể theo màu và hình dạng.',
  },
  {
    title: 'Nền tảng quản lý phòng Lab',
    field: 'SE',
    type: 'Production',
    status: 'Đang thực hiện',
    badge: 'ok',
    progress: '38%',
    cover: 'ph-se',
    members: ['NL', 'DA', 'KH', '+2'],
    description: 'Hệ thống quản lý nhân sự, dự án, nhiệm vụ và nội dung của Lab.',
  },
  {
    title: 'Xe tự hành mô hình tỉ lệ 1:10',
    field: 'Robotics',
    type: 'Research',
    status: 'Chuẩn bị',
    badge: 'warn',
    progress: '15%',
    cover: 'ph-robotics',
    members: ['DK', 'MT', '+2'],
    description: 'Nền tảng xe tự hành thu nhỏ dùng camera và LiDAR giá rẻ, phục vụ nghiên cứu điều khiển và định vị.',
  },
  {
    title: 'Trợ lý hỏi đáp tài liệu nội bộ',
    field: 'AI',
    type: 'NLP',
    status: 'Chuẩn bị',
    badge: 'warn',
    progress: '12%',
    cover: 'ph-ai',
    members: ['PH', 'TN', '+1'],
    description: 'Chatbot truy hồi tăng cường trả lời câu hỏi trên kho tài liệu và quy trình nội bộ của Lab.',
  },
  {
    title: 'Dashboard đánh giá đóng góp thành viên',
    field: 'SE',
    type: 'Analytics',
    status: 'Hoàn thành',
    badge: 'info',
    progress: '100%',
    cover: 'ph-se',
    members: ['LM', 'QT', 'VA'],
    description: 'Bộ dashboard theo dõi tiến độ, nhiệm vụ và điểm đánh giá theo tiêu chí của từng leader.',
  },
]

export const members = [
  { initials: 'VA', name: 'TS. Vũ Minh Anh', role: 'Trưởng phòng Lab', exp: 'Thị giác máy tính, học sâu', field: 'AI', color: 'var(--s1)' },
  { initials: 'TM', name: 'ThS. Trần Mạnh', role: 'Leader · Robotics', exp: 'Hệ nhúng, điều khiển tự động', field: 'Robotics', color: 'var(--s2)' },
  { initials: 'NL', name: 'Nguyễn Ngọc Lam', role: 'Leader · Software Engineering', exp: 'Kiến trúc hệ thống, DevOps', field: 'SE', color: 'var(--s3)' },
  { initials: 'PH', name: 'Phạm Hải', role: 'Member', exp: 'RAG, NLP, backend service', field: 'AI', color: 'var(--s5)' },
  { initials: 'DA', name: 'Đỗ Đức Anh', role: 'Member', exp: 'Frontend, UI system', field: 'SE', color: 'var(--s4)' },
  { initials: 'QD', name: 'Quách Duy', role: 'Member', exp: 'Robot arm, firmware', field: 'Robotics', color: 'var(--s2)' },
  { initials: 'KH', name: 'Kiều Hạnh', role: 'Member', exp: 'Testing, product workflow', field: 'SE', color: 'var(--s3)' },
  { initials: 'TN', name: 'Trần Ngọc', role: 'Member', exp: 'Data labeling, model eval', field: 'AI', color: 'var(--s1)' },
]

export const posts = [
  {
    title: 'Công bố bộ dữ liệu biển báo giao thông Việt Nam với 24.000 ảnh gán nhãn',
    category: 'Kết quả nghiên cứu',
    field: 'AI',
    date: '02/08/2026',
    read: '6 phút đọc',
    cover: 'ph-news',
    author: 'TS. Vũ Minh Anh · Nhóm AI',
    initials: 'VA',
    description: 'Bộ dữ liệu mở đầu tiên cho biển báo nội địa, kèm mô hình cơ sở đạt 94.2% mAP trên tập kiểm thử.',
  },
  {
    title: 'Mở đăng ký nhóm dự án Robotics kỳ Thu 2026',
    category: 'Thông báo',
    field: 'Robotics',
    date: '29/07/2026',
    read: '3 phút đọc',
    cover: 'ph-robotics',
    author: 'ThS. Trần Mạnh · Nhóm Robotics',
    initials: 'TM',
    description: 'Nhận thêm thành viên cho hai dự án cánh tay robot và xe tự hành, ưu tiên sinh viên năm 2-3.',
  },
  {
    title: 'Ba tháng đầu ở Lab: tôi đã học được gì',
    category: 'Chia sẻ kinh nghiệm',
    field: 'Community',
    date: '25/07/2026',
    read: '5 phút đọc',
    cover: 'ph-people',
    author: 'Phạm Hải · Thành viên năm 3',
    initials: 'PH',
    description: 'Ghi chép của một thành viên sau kỳ dự án đầu tiên, từ bỡ ngỡ tới đóng góp thật.',
  },
  {
    title: 'So sánh microservice và monolith cho hệ thống quy mô Lab',
    category: 'Bài viết học thuật',
    field: 'SE',
    date: '18/07/2026',
    read: '9 phút đọc',
    cover: 'ph-se',
    author: 'Nguyễn Ngọc Lam · Nhóm SE',
    initials: 'NL',
    description: 'Những tiêu chí kỹ thuật để chọn kiến trúc phù hợp với đội nhỏ nhưng sản phẩm cần mở rộng.',
  },
  {
    title: 'Hướng dẫn dựng pipeline gán nhãn ảnh với Label Studio',
    category: 'Hướng dẫn kỹ thuật',
    field: 'AI',
    date: '12/07/2026',
    read: '7 phút đọc',
    cover: 'ph-ai',
    author: 'Trần Ngọc · Nhóm AI',
    initials: 'TN',
    description: 'Quy trình thiết lập project, kiểm tra chất lượng nhãn và export dataset cho huấn luyện model.',
  },
  {
    title: 'Nhật ký 6 tháng phát triển cánh tay robot phân loại vật thể',
    category: 'Bài viết thành viên',
    field: 'Robotics',
    date: '28/07/2026',
    read: '8 phút đọc',
    cover: 'ph-robotics',
    author: 'Quách Duy · Nhóm Robotics',
    initials: 'QD',
    description: 'Từ prototype cơ khí, firmware tới calibration camera và các lỗi thường gặp khi demo.',
  },
]

export const documents = [
  {
    title: 'Quy chế vận hành Smart Lab',
    category: 'Quy trình nội bộ',
    updatedAt: '01/08/2026',
    description: 'Tổng quan cách Lab tổ chức role, project, nhiệm vụ, đánh giá và trách nhiệm của từng thành viên.',
  },
  {
    title: 'Hướng dẫn tham gia dự án nghiên cứu',
    category: 'Hướng dẫn thành viên',
    updatedAt: '28/07/2026',
    description: 'Các bước nhận project, làm việc với leader, cập nhật tiến độ và nộp kết quả định kỳ trên hệ thống.',
  },
  {
    title: 'Template báo cáo tiến độ',
    category: 'Biểu mẫu',
    updatedAt: '20/07/2026',
    description: 'Mẫu cấu trúc báo cáo ngắn cho demo, review kỹ thuật và đánh giá cuối giai đoạn.',
  },
]

export const events = [
  { day: '12', month: '08', title: 'Workshop: Xây pipeline dữ liệu ảnh', meta: 'A3-502 · 19:00 · Nhóm AI' },
  { day: '19', month: '08', title: 'Demo nội bộ cánh tay robot', meta: 'Lab Robotics · 18:30 · Nhóm Robotics' },
  { day: '26', month: '08', title: 'Review kiến trúc Smart Lab Platform', meta: 'Online · 20:00 · Nhóm SE' },
  { day: '04', month: '09', title: 'Bảo vệ tiến độ tháng 09', meta: 'Hội trường B · 17:30 · Toàn Lab' },
]

export const gallery = [
  { title: 'Workshop AI', cls: 'ph-ai wide' },
  { title: 'Robot arm demo', cls: 'ph-robotics' },
  { title: 'Code review', cls: 'ph-se' },
  { title: 'Seminar nội bộ', cls: 'ph-news tall' },
  { title: 'Data labeling', cls: 'ph-people' },
  { title: 'Sprint planning', cls: 'ph-se wide' },
]
