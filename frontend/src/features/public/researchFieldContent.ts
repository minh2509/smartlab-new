/**
 * Curated academic domain knowledge for research fields.
 * Mapped by stable research field code (e.g. AI, ROBOTICS, SE).
 *
 * Authoritative Sources:
 * - AI: NIST AI Risk Management Framework (NIST SP 1270), NIST CSRC AI Glossary, Stanford Artificial Intelligence Laboratory (SAIL), Stanford HAI.
 * - ROBOTICS: ISO 8373:2021 (Robotics — Vocabulary), International Federation of Robotics (IFR).
 * - SOFTWARE ENGINEERING: IEEE Computer Society Guide to the Software Engineering Body of Knowledge (SWEBOK Guide V4.0a).
 *
 * DATA TRUTH PRINCIPLE:
 * These descriptions articulate general scientific & engineering domain knowledge.
 * They are intentionally distinct from SmartLab-specific operational data (which comes from the database).
 */

export interface SubdomainItem {
  title: string
  englishTitle: string
  description: string
  focusAreas: string[]
}

export interface LifecyclePhase {
  step: string
  name: string
  englishName: string
  objective: string
  activities: string
  deliverables: string
}

export interface TechnicalFoundation {
  index: string
  title: string
  englishTitle: string
  description: string
  disciplines: string[]
}

export interface QualityPillar {
  title: string
  englishTitle: string
  description: string
}

export interface AcademicReference {
  institution: string
  title: string
  url: string
  domain: string
  scope: string
}

export interface ResearchFieldDomainContent {
  code: string
  vietnameseName: string
  englishName: string
  tagline: string
  overview: {
    definition: string
    definitionSource: string
    problemSpace: string
    distinction: string
  }
  researchMap: {
    sectionDescription: string
    items: SubdomainItem[]
  }
  lifecycle: {
    sectionDescription: string
    disclaimer: string
    phases: LifecyclePhase[]
  }
  foundations: {
    sectionDescription: string
    items: TechnicalFoundation[]
  }
  responsibility: {
    title: string
    framework: string
    description: string
    pillars: QualityPillar[]
  }
  references: AcademicReference[]
}

const RESEARCH_FIELD_CONTENTS: Record<string, ResearchFieldDomainContent> = {
  AI: {
    code: 'AI',
    vietnameseName: 'Trí tuệ nhân tạo',
    englishName: 'Artificial Intelligence',
    tagline: 'Kỹ nghệ hệ thống thông minh, mô hình hóa dữ liệu và học máy tự thích ứng',
    overview: {
      definition:
        'Theo Viện Tiêu chuẩn và Công nghệ Quốc gia Hoa Kỳ (NIST SP 1270), Trí tuệ Nhân tạo (AI) là hệ thống kỹ thuật hoạt động dựa trên máy tính, có khả năng diễn giải bối cảnh và tạo ra các kết quả như dự đoán, nội dung, khuyến nghị hoặc quyết định tác động đến môi trường vật lý hoặc môi trường ảo nhằm phục vụ một tập hợp các mục tiêu do con người xác định.',
      definitionSource: 'NIST AI Risk Management Framework (AI RMF 1.0) & NIST CSRC Glossary',
      problemSpace:
        'AI giải quyết các bài toán có tính bất định cao, không gian tìm kiếm tổ hợp khổng lồ hoặc dữ liệu phi cấu trúc phức tạp (như hình ảnh, tiếng nói, văn bản tự nhiên, chuỗi thời gian) — những bài toán mà thuật toán tất định truyền thống khó có thể lập trình tường minh từng quy tắc.',
      distinction:
        'Khác với kỹ nghệ phần mềm truyền thống (vốn dựa trên logic mã nguồn được lập trình tĩnh), hệ thống AI suy diễn quy luật từ dữ liệu thông qua tối ưu hóa toán học và hàm mất mát. Đồng thời, AI khác với phân tích thống kê thuần túy ở khả năng tự học biểu diễn (representation learning) và tự khái quát hóa trên dữ liệu mới chưa từng thấy.',
    },
    researchMap: {
      sectionDescription:
        'Cấu trúc các hướng nghiên cứu trọng tâm trong khoa học AI hiện đại, tham chiếu theo mô hình nghiên cứu của Stanford Artificial Intelligence Laboratory (SAIL).',
      items: [
        {
          title: 'Học máy & Lý thuyết học',
          englishTitle: 'Machine Learning & Statistical Learning Theory',
          description:
            'Nghiên cứu nền tảng toán học của việc khái quát hóa dữ liệu, giải thuật học có giám sát, không giám sát, học bán giám sát và phương pháp tăng cường dữ liệu.',
          focusAreas: ['Statistical Learning', 'Loss Landscape Optimization', 'Generalization Bounds', 'Meta-Learning'],
        },
        {
          title: 'Học sâu & Mô hình nền tảng',
          englishTitle: 'Deep Learning & Foundation Models',
          description:
            'Thiết kế kiến trúc mạng nơ-ron đa tầng, cơ chế chú ý (Attention), mạng biến đổi (Transformer) và các mô hình biểu diễn đa phương thức quy mô lớn.',
          focusAreas: ['Transformer Architectures', 'Diffusion Models', 'Representation Learning', 'Parameter-Efficient Fine-Tuning'],
        },
        {
          title: 'Thị giác máy tính',
          englishTitle: 'Computer Vision & Visual Intelligence',
          description:
            'Xử lý, phân tích và diễn giải dữ liệu thị giác số: nhận dạng vật thể, phân đoạn ngữ nghĩa ảnh, ước lượng tư thế 3D và tái tạo không gian từ cảm biến hình ảnh.',
          focusAreas: ['Object Detection', 'Semantic Segmentation', '3D Scene Reconstruction', 'Neural Radiance Fields (NeRF)'],
        },
        {
          title: 'Xử lý ngôn ngữ tự nhiên & Tiếng nói',
          englishTitle: 'Natural Language Processing & Speech',
          description:
            'Mô hình hóa ngôn ngữ con người, trích xuất thông tin thực thể, dịch máy, đối thoại thông minh, tổng hợp và nhận dạng tiếng nói tự động.',
          focusAreas: ['Information Extraction', 'Large Language Models', 'Speech Recognition & Synthesis', 'Semantic Parsing'],
        },
        {
          title: 'Học tăng cường & Quyết định tự hành',
          englishTitle: 'Reinforcement Learning & Decision Making',
          description:
            'Nghiên cứu cơ chế tác nhân (agent) tương tác với môi trường thông qua trạng thái, hành động và tín hiệu phần thưởng nhằm tối ưu hóa chính sách hành động dài hạn.',
          focusAreas: ['Markov Decision Processes', 'Policy Gradient Methods', 'Model-Based RL', 'Multi-Agent Reinforcement Learning'],
        },
        {
          title: 'AI lấy con người làm trung tâm & Trách nhiệm',
          englishTitle: 'Human-Centered & Trustworthy AI',
          description:
            'Đảm bảo hệ thống AI minh bạch, có thể giải thích được, giảm thiểu sai lệch dữ liệu, bảo vệ quyền riêng tư và tương tác an toàn với người dùng.',
          focusAreas: ['Explainable AI (XAI)', 'Algorithmic Fairness', 'Differential Privacy', 'Human-AI Collaboration'],
        },
      ],
    },
    lifecycle: {
      sectionDescription:
        'Vòng đời nghiên cứu và triển khai một hệ thống AI theo chuẩn thực hành kỹ nghệ hiện đại, kết hợp phương pháp luận phát triển mô hình và kiểm định TEVV (NIST).',
      disclaimer:
        'Lưu ý: Sơ đồ dưới đây thể hiện phương pháp luận kỹ nghệ tổng quát của ngành khoa học AI, không phải quy trình bắt buộc cho mọi dự án tại Smart Lab.',
      phases: [
        {
          step: '01',
          name: 'Xác định bài toán & Yêu cầu',
          englishName: 'Problem Formulation & Scoping',
          objective: 'Chuyển đổi bài toán ứng dụng thực tế thành bài toán học máy định lượng.',
          activities: 'Phân tích tính khả thi, định nghĩa metric đo lường (F1, BLEU, mAP, Latency) và đánh giá rủi ro đạo đức.',
          deliverables: 'Đặc tả yêu cầu kỹ thuật & Khung đo lường kiểm định.',
        },
        {
          step: '02',
          name: 'Kỹ nghệ & Quản trị dữ liệu',
          englishName: 'Data Curation & Governance',
          objective: 'Thu thập, làm sạch và bảo đảm chất lượng tập dữ liệu huấn luyện.',
          activities: 'Thu thập dữ liệu thô, gán nhãn chuẩn hóa, kiểm tra phân phối dữ liệu, phát hiện bias và phân tách train/val/test.',
          deliverables: 'Tập dữ liệu đã chuẩn hóa, phiên bản hóa dữ liệu (DVC).',
        },
        {
          step: '03',
          name: 'Thiết kế kiến trúc & Huấn luyện',
          englishName: 'Model Architecture & Training',
          objective: 'Xây dựng cấu trúc mô hình tối ưu và huấn luyện trên hạ tầng tính toán.',
          activities: 'Lựa chọn backbone kiến trúc, thiết kế hàm mất mát, dò siêu tham số, tối ưu hóa quá trình tính toán trên GPU/TPU.',
          deliverables: 'Mô hình nền tảng, checkpoint trọng số, log huấn luyện.',
        },
        {
          step: '04',
          name: 'Kiểm định, Đánh giá & TEVV',
          englishName: 'Test, Evaluation, Verification & Validation',
          objective: 'Thử nghiệm nghiêm ngặt về độ chính xác, tính ổn định và tính an toàn theo khung NIST.',
          activities: 'Kiểm thử trên benchmark độc lập, kiểm tra độ nhạy (stress test), kiểm thử đối kháng (adversarial attacks), phân tích lỗi.',
          deliverables: 'Báo cáo kiểm định mô hình (Model Card & Evaluation Report).',
        },
        {
          step: '05',
          name: 'Tối ưu hóa & Triển khai',
          englishName: 'Optimization & Deployment',
          objective: 'Đóng gói mô hình phục vụ suy luận thực tế với độ trễ và tài nguyên tối ưu.',
          activities: 'Lượng tử hóa (Quantization), tỉa thưa (Pruning), biên dịch TensorRT/ONNX, tích hợp API microservice.',
          deliverables: 'Serving container, Endpoint API suy luận thời gian thực.',
        },
        {
          step: '06',
          name: 'Giám sát trôi & Tái thích ứng',
          englishName: 'Monitoring & Drift Adaptation',
          objective: 'Theo dõi hiệu năng thực tế và ngăn ngừa suy giảm chất lượng theo thời gian.',
          activities: 'Phát hiện Data Drift / Concept Drift, thu thập phản hồi người dùng, tái huấn luyện định kỳ (Continuous Learning).',
          deliverables: 'Hệ thống telemetry, pipeline tự động tái huấn luyện.',
        },
      ],
    },
    foundations: {
      sectionDescription:
        'Các nền tảng lý thuyết bắt buộc giúp hiểu sâu bản chất các mô hình thông minh và không dừng lại ở mức độ gọi thư viện đen (black-box API).',
      items: [
        {
          index: '01',
          title: 'Đại số tuyến tính & Giải tích đa biến',
          englishTitle: 'Linear Algebra & Multivariate Calculus',
          description: 'Không gian vector, ma trận, phân rã giá trị dị thường (SVD), phép nhân tensor, vi phân ma trận và giải thuật hạ gradient.',
          disciplines: ['Matrix Decompositions', 'Eigenvalues & Eigenvectors', 'Jacobians & Hessians', 'Tensor Operations'],
        },
        {
          index: '02',
          title: 'Xác suất & Thống kê suy diễn',
          englishTitle: 'Probability & Inferential Statistics',
          description: 'Biến ngẫu nhiên, phân phối xác suất, định lý Bayes, ước lượng hợp lý cực đại (MLE), suy diễn biến phân và kiểm định giả thuyết.',
          disciplines: ['Bayesian Inference', 'Maximum Likelihood', 'Information Theory (Entropy)', 'Markov Chains'],
        },
        {
          index: '03',
          title: 'Lý thuyết tối ưu hóa',
          englishTitle: 'Mathematical Optimization',
          description: 'Quy hoạch lồi, hàm đối ngẫu Lagrange, phương pháp hạ gradient ngẫu nhiên (SGD), Adam, RMSProp và kiểm soát hội tụ.',
          disciplines: ['Convex Optimization', 'Constrained Optimization', 'Stochastic Gradient Methods', 'Learning Rate Schedules'],
        },
        {
          index: '04',
          title: 'Cấu trúc dữ liệu & Giải thuật',
          englishTitle: 'Data Structures & Algorithms',
          description: 'Độ phức tạp tính toán, đồ thị, cây quyết định, giải thuật tìm kiếm heuristic, quy hoạch động và lập trình song song.',
          disciplines: ['Computational Complexity', 'Graph Traversal', 'Search Heuristics', 'Vectorized Processing'],
        },
        {
          index: '05',
          title: 'Kỹ nghệ dữ liệu & Đường ống xử lý',
          englishTitle: 'Data Engineering & MLOps',
          description: 'Xử lý luồng dữ liệu, lưu trữ phân tán, schema dữ liệu, quản lý phiên bản mã và dữ liệu, CI/CD cho mô hình học máy.',
          disciplines: ['ETL Pipelines', 'Feature Stores', 'Distributed Storage', 'Containerization & Orchestration'],
        },
        {
          index: '06',
          title: 'Đánh giá mô hình & Benchmark',
          englishTitle: 'Model Evaluation & Benchmarking',
          description: 'Kiểm tra chéo (k-fold), phân tích ma trận nhầm lẫn (confusion matrix), ROC-AUC, calibration curve và đo lường suy diễn.',
          disciplines: ['Cross-Validation', 'Classification Metrics', 'Error Analysis', 'Latency/Throughput Benchmarking'],
        },
      ],
    },
    responsibility: {
      title: 'Độ tin cậy & Chuẩn mực đạo đức AI',
      framework: 'Tham chiếu theo NIST AI Risk Management Framework (NIST SP 1270)',
      description:
        'Nghiên cứu AI hiện đại đòi hỏi sự nghiêm túc vượt trên độ chính xác đơn thuần. Một hệ thống AI chỉ có giá trị thực tiễn bền vững khi đáp ứng được các đặc tính cốt lõi của Trustworthy AI do NIST xác lập.',
      pillars: [
        {
          title: 'Hợp lệ & Ổn định',
          englishTitle: 'Valid & Reliable',
          description: 'Hệ thống phải hoạt động chính xác theo đặc tả kỹ thuật và duy trì chất lượng ổn định trong các điều kiện thực tế khác nhau.',
        },
        {
          title: 'An toàn hệ thống',
          englishTitle: 'Safe',
          description: 'Không gây ra nguy cơ tổn hại về thể chất, tinh thần, xã hội hay tài sản cho người dùng và môi trường xung quanh.',
        },
        {
          title: 'An ninh & Khả năng phục hồi',
          englishTitle: 'Secure & Resilient',
          description: 'Chống chịu trước các cuộc tấn công đối kháng (adversarial), xâm nhập dữ liệu và có khả năng tự phục hồi sau sự cố.',
        },
        {
          title: 'Trách nhiệm & Minh bạch',
          englishTitle: 'Accountable & Transparent',
          description: 'Quy trình huấn luyện, nguồn dữ liệu và quyết định của mô hình có thể được truy xuất và giải trình rõ ràng.',
        },
        {
          title: 'Khả năng giải thích',
          englishTitle: 'Explainable & Interpretable',
          description: 'Cung cấp cơ chế cho phép con người hiểu được lý do mô hình đưa ra một dự đoán hay đề xuất cụ thể.',
        },
        {
          title: 'Quản trị rủi ro thiên vị & Công bằng',
          englishTitle: 'Fair with Harmful Bias Managed',
          description: 'Chủ động đo lường và loại bỏ các sai lệch phân biệt đối xử xuất phát từ dữ liệu lịch sử hoặc giải thuật.',
        },
      ],
    },
    references: [
      {
        institution: 'National Institute of Standards and Technology (NIST)',
        title: 'Artificial Intelligence Risk Management Framework (AI RMF 1.0) — NIST SP 1270',
        url: 'https://www.nist.gov/itl/ai-risk-management-framework',
        domain: 'nist.gov',
        scope: 'Khung tiêu chuẩn quản trị rủi ro và các đặc tính độ tin cậy của hệ thống AI.',
      },
      {
        institution: 'NIST Computer Security Resource Center',
        title: 'NIST Glossary of Key Information Security & AI Terms',
        url: 'https://csrc.nist.gov/glossary/term/artificial_intelligence',
        domain: 'csrc.nist.gov',
        scope: 'Định nghĩa chuẩn mực về thuật ngữ và thành phần hệ thống AI.',
      },
      {
        institution: 'Stanford University — Computer Science Department',
        title: 'Stanford Artificial Intelligence Laboratory (SAIL) Research Overview',
        url: 'https://ai.stanford.edu/research-groups/',
        domain: 'ai.stanford.edu',
        scope: 'Mô hình phân loại các nhóm nghiên cứu chuyên sâu trong khoa học AI.',
      },
      {
        institution: 'Stanford Institute for Human-Centered Artificial Intelligence',
        title: 'Stanford HAI AI Index Report',
        url: 'https://hai.stanford.edu/ai-index',
        domain: 'hai.stanford.edu',
        scope: 'Báo cáo toàn cảnh xu hướng kỹ thuật, ứng dụng và đạo đức AI toàn cầu.',
      },
    ],
  },

  ROBOTICS: {
    code: 'ROBOTICS',
    vietnameseName: 'Robot học & Hệ thống tự hành',
    englishName: 'Robotics',
    tagline: 'Kỹ nghệ cơ điện tử thông minh, nhận thức môi trường và điều khiển vòng lặp kín',
    overview: {
      definition:
        'Theo tiêu chuẩn quốc tế ISO 8373:2021 (Robotics — Vocabulary), robot là cơ cấu chấp hành được lập trình trên hai hoặc nhiều trục, có mức độ tự hành nhất định, có khả năng di chuyển hoặc thao tác trong môi trường để thực hiện các nhiệm vụ được giao.',
      definitionSource: 'ISO 8373:2021 International Standard for Robotics',
      problemSpace:
        'Robot học giải quyết sự tương tác vật lý trực tiếp giữa máy tính và thế giới thực trong điều kiện bất định. Trọng tâm là chuyển đổi các dòng dữ liệu cảm biến thành hành vi vật lý chính xác, ổn định và an toàn.',
      distinction:
        'Khác với tự động hóa cố định truyền thống (chỉ lặp lại chu trình cứng), robot hiện đại sở hữu mức độ tự hành (autonomy) thông qua cảm biến nhận thức, phản hồi vòng lặp kín và lập kế hoạch linh hoạt theo sự biến đổi của môi trường làm việc.',
    },
    researchMap: {
      sectionDescription:
        'Bản đồ các hướng nghiên cứu cốt lõi trong kỹ thuật robot hiện đại, tổng hợp theo phân loại của ISO và Hiệp hội Robot Quốc tế (IFR).',
      items: [
        {
          title: 'Nhận thức & Hợp nhất cảm biến',
          englishTitle: 'Perception & Sensor Fusion',
          description:
            'Thu thập và tích hợp thông tin từ nhiều nguồn cảm biến (LiDAR, máy ảnh độ sâu RGB-D, IMU, encoder) để ước lượng trạng thái môi trường và vật thể.',
          focusAreas: ['Kalman Filtering', 'Visual-Inertial Odometry (VIO)', 'Point Cloud Processing', 'Multi-modal Fusion'],
        },
        {
          title: 'Định vị & Bản đồ hóa đồng thời',
          englishTitle: 'Simultaneous Localization & Mapping (SLAM)',
          description:
            'Xây dựng bản đồ không gian chưa biết đồng thời xác định vị trí chính xác của robot theo thời gian thực mà không phụ thuộc GPS trong nhà.',
          focusAreas: ['LiDAR SLAM', 'Visual SLAM', 'Loop Closure Detection', 'Occupancy Grid Mapping'],
        },
        {
          title: 'Động học & Điều khiển chuyển động',
          englishTitle: 'Kinematics, Dynamics & Motion Control',
          description:
            'Mô hình hóa hình học chuyển động (thuận/nghịch), động lực học ma sát/quán tính và thiết kế bộ điều khiển phản hồi PID, LQR, MPC.',
          focusAreas: ['Forward & Inverse Kinematics', 'Model Predictive Control (MPC)', 'Trajectory Generation', 'Computed Torque Control'],
        },
        {
          title: 'Thao tác & Khéo léo cơ học',
          englishTitle: 'Manipulation & Robotic Grasping',
          description:
            'Nghiên cứu cánh tay robot nhiều bậc tự do, bộ gắp (gripper/end-effector), điều khiển lực tiếp xúc và gắp thả vật thể không định hình.',
          focusAreas: ['Grasp Planning', 'Impedance & Admittance Control', 'Tactile Sensing', 'Motion Primitives'],
        },
        {
          title: 'Robot di động & Dẫn đường tự hành',
          englishTitle: 'Mobile Robotics & Autonomous Navigation',
          description:
            'Hệ thống xe tự hành AGV/AMR bánh lốp, robot chân nhện/chân người, lập kế hoạch đường đi toàn cục và né tránh chướng ngại vật cục bộ.',
          focusAreas: ['Global Path Planning (A*, RRT*)', 'Local Obstacle Avoidance (DWA)', 'Wheel Odometry', 'Fleet Coordination'],
        },
        {
          title: 'Tương tác Người - Robot & Robot cộng tác',
          englishTitle: 'Human-Robot Interaction & Cobots',
          description:
            'Thiết kế robot cộng tác (Cobot) làm việc chung không gian với con người, nhận diện cử chỉ, an toàn tiếp xúc và chia sẻ quyền điều khiển.',
          focusAreas: ['Shared Autonomy', 'Contact Force Limitation', 'Gesture Recognition', 'Ergonomic Workstation Co-working'],
        },
      ],
    },
    lifecycle: {
      sectionDescription:
        'Vòng đời phát triển và thử nghiệm một hệ thống robot tự hành điển hình từ thiết kế cơ điện tử đến hiệu chuẩn thực địa.',
      disclaimer:
        'Lưu ý: Sơ đồ dưới đây thể hiện chu trình kỹ nghệ robot tổng quát, không phải quy trình bắt buộc cho mọi dự án tại Smart Lab.',
      phases: [
        {
          step: '01',
          name: 'Phân tích nhiệm vụ & Môi trường',
          englishName: 'Task & Environment Requirements',
          objective: 'Xác định không gian làm việc, tải trọng, độ chính xác và rủi ro an toàn.',
          activities: 'Khảo sát môi trường thực địa, tính toán bậc tự do (DoF), xác định giới hạn thời gian phản hồi thời gian thực.',
          deliverables: 'Đặc tả yêu cầu cơ điện tử & Ma trận phân tích rủi ro an toàn.',
        },
        {
          step: '02',
          name: 'Thiết kế cơ điện tử & Mô phỏng',
          englishName: 'Mechatronics Design & Simulation',
          objective: 'Xây dựng mô hình hình học, chọn động cơ/cảm biến và mô phỏng số học.',
          activities: 'Thiết kế CAD 3D, chọn động cơ bước/servo, mô phỏng vật lý trong môi trường Gazebo / Webots / Isaac Sim.',
          deliverables: 'Bản vẽ cơ khí CAD, sơ đồ mạch điện tử, mô hình URDF/SDF.',
        },
        {
          step: '03',
          name: 'Tích hợp phần cứng & Hệ thống nhúng',
          englishName: 'Hardware Assembly & Embedded Systems',
          objective: 'Hiện thực hóa nền tảng vật lý và lập trình firmware điều khiển cấp thấp.',
          activities: 'Gia công chi tiết, lắp ráp mạch driver công suất, lập trình vi điều khiển STM32/ESP32, cấu hình mạng CAN/I2C.',
          deliverables: 'Khung robot vật lý, firmware điều khiển cấp thấp hoạt động.',
        },
        {
          step: '04',
          name: 'Phát triển phần mềm điều khiển & ROS',
          englishName: 'Control Software & Middleware (ROS 2)',
          objective: 'Phát triển tầng nhận thức, dẫn đường và điều khiển tự động cấp cao.',
          activities: 'Tích hợp ROS 2 nodes, viết driver cảm biến, triển khai thuật toán SLAM, path planning và state machine điều phối.',
          deliverables: 'Gói phần mềm ROS 2 hoàn chỉnh, launch files cấu hình.',
        },
        {
          step: '05',
          name: 'Hiệu chuẩn & Kiểm thử vòng lặp kín',
          englishName: 'Calibration & Closed-Loop Testing',
          objective: 'Hiệu chuẩn thông số cảm biến và bảo đảm vòng lặp phản hồi ổn định.',
          activities: 'Hiệu chuẩn camera-IMU, cân chỉnh PID gain, đo lường sai số lặp lại, kiểm thử nút dừng khẩn cấp (E-Stop).',
          deliverables: 'Bảng thông số hiệu chuẩn, báo cáo độ ổn định điều khiển.',
        },
        {
          step: '06',
          name: 'Thử nghiệm hiện trường & Giám sát',
          englishName: 'Field Validation & Continuous Telemetry',
          objective: 'Kiểm chứng khả năng thực thi nhiệm vụ trong môi trường vận hành thực tế.',
          activities: 'Chạy thử nghiệm dài hạn (endurance test), thu thập log telemetry, đánh giá độ mỏi cơ học và cải tiến thuật toán.',
          deliverables: 'Báo cáo nghiệm thu vận hành thực địa, sổ tay bảo trì kỹ thuật.',
        },
      ],
    },
    foundations: {
      sectionDescription:
        'Hệ thống tri thức đa ngành hội tụ trong kỹ thuật robot, kết hợp giữa cơ học lý thuyết, kỹ thuật điện và khoa học máy tính.',
      items: [
        {
          index: '01',
          title: 'Cơ học lý thuyết & Động lực học nhiều vật',
          englishTitle: 'Classical Mechanics & Multi-Body Dynamics',
          description: 'Cơ học Newton-Euler, phương trình Lagrange, ma trận quán tính, động học quay (quaternion, Euler angles) và ma trận chuyển đổi Denavit-Hartenberg (DH).',
          disciplines: ['Denavit-Hartenberg Parameters', 'Euler-Lagrange Equations', 'Jacobian Transformations', 'Rigid Body Motion'],
        },
        {
          index: '02',
          title: 'Lý thuyết điều khiển tự động',
          englishTitle: 'Feedback Control Systems Theory',
          description: 'Hệ thống tuyến tính và phi tuyến, không gian trạng thái (state-space), tiêu chuẩn ổn định Lyapunov, điều khiển PID, LQR và điều khiển thích nghi.',
          disciplines: ['State-Space Representation', 'Lyapunov Stability', 'Optimal Control (LQR)', 'Sliding Mode Control'],
        },
        {
          index: '03',
          title: 'Hệ thống nhúng & Hệ điều hành thời gian thực',
          englishTitle: 'Embedded Systems & Real-Time OS (RTOS)',
          description: 'Kiến trúc vi điều khiển ARM Cortex, FreeRTOS, lập trình bất đồng bộ, ngắt ngoại vi, giao tiếp công nghiệp CAN bus, SPI, UART.',
          disciplines: ['Interrupt Service Routines', 'Real-Time Task Scheduling', 'CAN/EtherCAT Protocols', 'PWM Motor Drives'],
        },
        {
          index: '04',
          title: 'Xử lý tín hiệu cảm biến & Lọc số',
          englishTitle: 'Sensor Signal Processing & State Estimation',
          description: 'Bộ lọc bù, bộ lọc Kalman mở rộng (EKF), bộ lọc hạt (Particle Filter), xử lý dữ liệu đám mây điểm 3D và khử nhiễu tín hiệu.',
          disciplines: ['Extended Kalman Filter (EKF)', 'Unscented Kalman Filter (UKF)', 'Digital Signal Filtering', 'IMU Calibration'],
        },
        {
          index: '05',
          title: 'Phần mềm trung gian robot (ROS 2)',
          englishTitle: 'Robotic Middleware Architecture (ROS 2)',
          description: 'Mô hình xuất bản/đăng ký (Pub/Sub), dịch vụ (Service), hành động (Action), giao tiếp DDS thời gian thực, quản lý vòng đời node và tf2 coordinate transformation.',
          disciplines: ['DDS Quality of Service (QoS)', 'tf2 Transform Library', 'Lifecycle Nodes', 'Action Servers'],
        },
        {
          index: '06',
          title: 'Kỹ thuật cơ khí & Chế tạo nguyên mẫu',
          englishTitle: 'Mechanical Prototyping & Actuation',
          description: 'Lựa chọn hộp số giảm tốc (harmonic, hành tinh), thiết kế liên kết chịu lực, in 3D kỹ thuật, gia công CNC và an toàn cơ khí.',
          disciplines: ['Gear Ratio Selection', 'Stress Analysis (FEA)', 'Rapid Prototyping', 'Backlash Minimization'],
        },
      ],
    },
    responsibility: {
      title: 'An toàn chức năng & Chuẩn mực vận hành Robot',
      framework: 'Tham chiếu tiêu chuẩn an toàn ISO 10218 & ISO 13482',
      description:
        'Do đặc thù tương tác trực tiếp bằng lực vật lý, kỹ nghệ robot đặt tiêu chuẩn an toàn (Safety-First) lên hàng đầu. Một thiết kế kỹ thuật không đạt chuẩn an toàn chức năng không được phép triển khai vào thực tế.',
      pillars: [
        {
          title: 'Cơ chế ngắt an toàn & E-Stop',
          englishTitle: 'Fail-Safe & Emergency Stop',
          description: 'Khi phát hiện mất tín hiệu điều khiển hoặc sai số vị trí vượt ngưỡng, hệ thống phải tự động chuyển sang trạng thái dừng an toàn (fail-safe state).',
        },
        {
          title: 'Ranh giới tự hành có kiểm soát',
          englishTitle: 'Autonomy Boundaries & Geo-fencing',
          description: 'Robot chỉ được phép hoạt động tự hành trong không gian xác định với các hàng rào an toàn vật lý hoặc hàng rào ảo (virtual barriers).',
        },
        {
          title: 'Giới hạn lực & Công suất tiếp xúc',
          englishTitle: 'Power & Force Limiting (PFL)',
          description: 'Áp dụng theo ISO/TS 15066 đối với robot cộng tác: lực và áp lực tiếp xúc khi va chạm bất ngờ với con người không bao giờ được vượt ngưỡng an toàn sinh học.',
        },
        {
          title: 'Xác minh & Độc lập giám sát',
          englishTitle: 'Dual-Channel Monitoring & Redundancy',
          description: 'Hệ thống an toàn sử dụng kênh cảm biến và mạch ngắt độc lập, không dùng chung luồng phần mềm chính để ngăn ngừa lỗi đơn lẻ (single-point failure).',
        },
      ],
    },
    references: [
      {
        institution: 'International Organization for Standardization (ISO)',
        title: 'ISO 8373:2021 Robotics — Vocabulary',
        url: 'https://www.iso.org/standard/75539.html',
        domain: 'iso.org',
        scope: 'Định nghĩa chuẩn hóa quốc tế về các thuật ngữ, cơ cấu và mức độ tự hành trong robot học.',
      },
      {
        institution: 'International Federation of Robotics (IFR)',
        title: 'IFR Industrial Robots & Service Robots Technical Classifications',
        url: 'https://ifr.org/industrial-robots',
        domain: 'ifr.org',
        scope: 'Báo cáo kỹ thuật và xu hướng phân loại ứng dụng robot công nghiệp và dịch vụ.',
      },
      {
        institution: 'International Federation of Robotics (IFR)',
        title: 'Service Robots Technology and Application Overview',
        url: 'https://ifr.org/service-robots',
        domain: 'ifr.org',
        scope: 'Tổng quan công nghệ robot dịch vụ, AMR và hệ thống tự hành ngoài công nghiệp.',
      },
    ],
  },

  SE: {
    code: 'SE',
    vietnameseName: 'Kỹ nghệ phần mềm',
    englishName: 'Software Engineering',
    tagline: 'Phương pháp luận kỹ nghệ kỷ luật, kiến trúc hệ thống bền vững và đảm bảo chất lượng toàn diện',
    overview: {
      definition:
        'Theo Hội Điện toán IEEE (IEEE Computer Society) trong SWEBOK Guide V4.0a, Kỹ nghệ Phần mềm (Software Engineering) là việc áp dụng một cách tiếp cận có hệ thống, kỷ luật, có thể định lượng được vào việc phát triển, vận hành và bảo trì phần mềm; nói cách khác, đó là việc áp dụng các nguyên lý kỹ nghệ vào phần mềm.',
      definitionSource: 'IEEE Computer Society SWEBOK Guide V4.0a (Body of Knowledge)',
      problemSpace:
        'Kỹ nghệ phần mềm giải quyết thách thức kiểm soát độ phức tạp (complexity control), quy mô mở rộng (scalability), tính bền vững theo thời gian và chất lượng của các hệ thống thông tin quy mô lớn được xây dựng bởi nhiều nhóm kỹ sư phối hợp.',
      distinction:
        'Khác với hoạt động viết mã (coding) đơn lẻ hay lập trình giải thuật tự phát, kỹ nghệ phần mềm bao trùm toàn bộ vòng đời sản phẩm: từ thu thập phân tích yêu cầu, thiết kế kiến trúc, kiểm thử tự động đa tầng, quản lý cấu hình, triển khai liên tục đến bảo trì tiến hóa và kiểm soát nợ kỹ thuật.',
    },
    researchMap: {
      sectionDescription:
        'Cấu trúc các vùng tri thức nòng cốt (Knowledge Areas) được chắt lọc từ SWEBOK V4.0a thành 5 cụm kiến trúc kỹ nghệ trực quan.',
      items: [
        {
          title: 'Khám phá & Kỹ nghệ yêu cầu',
          englishTitle: 'Software Requirements & Discovery',
          description:
            'Nghiên cứu phương pháp thu thập, phân tích, mô hình hóa, đặc tả và thẩm định yêu cầu chức năng cũng như phi chức năng của hệ thống phần mềm.',
          focusAreas: ['Requirements Elicitation', 'Traceability Analysis', 'Formal Specifications', 'Non-Functional Requirements Modeling'],
        },
        {
          title: 'Kiến trúc & Thiết kế hệ thống',
          englishTitle: 'Software Architecture & System Design',
          description:
            'Thiết kế cấu trúc phân rã module, mẫu kiến trúc (Microservices, Event-Driven, Clean Architecture), giao diện API và chiến lược dung sai lỗi.',
          focusAreas: ['Architectural Trade-off Analysis (ATAM)', 'Design Patterns', 'API Contracts & Schemas', 'Distributed Concurrency'],
        },
        {
          title: 'Hiện thực hóa & Kiểm định tự động',
          englishTitle: 'Software Construction & Testing',
          description:
            'Nguyên lý viết mã nguồn sạch, tái cấu trúc mã (Refactoring), chiến lược kiểm thử tự động đa cấp từ Unit, Integration đến Contract và Performance Testing.',
          focusAreas: ['Clean Code Principles', 'Automated Test Suites', 'Mutation Testing', 'Static Code Analysis & Linting'],
        },
        {
          title: 'Tiến hóa & Quản lý cấu hình',
          englishTitle: 'Software Maintenance & Evolution',
          description:
            'Nghiên cứu kiểm soát nợ kỹ thuật (Technical Debt), bảo trì thích ứng, quản lý phiên bản phần mềm, quy trình phân nhánh mã nguồn và di chuyển hệ thống cũ.',
          focusAreas: ['Technical Debt Management', 'Semantic Versioning', 'Git Flow & Trunk-Based', 'Legacy System Modernization'],
        },
        {
          title: 'Kỹ nghệ quy mô, DevOps & Vận hành',
          englishTitle: 'Engineering at Scale, DevOps & Operations',
          description:
            'Xây dựng đường ống CI/CD, cơ sở hạ tầng dưới dạng mã (IaC), khả năng quan sát hệ thống (Observability: Metrics, Logs, Traces) và phản hồi sự cố.',
          focusAreas: ['Continuous Delivery Pipelines', 'Infrastructure as Code (IaC)', 'Site Reliability Engineering (SRE)', 'System Observability'],
        },
        {
          title: 'An ninh phần mềm theo thiết kế',
          englishTitle: 'Software Security by Design',
          description:
            'Tích hợp an ninh xuyên suốt vòng đời (DevSecOps), mô hình hóa mối đe dọa (Threat Modeling), kiểm thử bảo mật động/tĩnh (DAST/SAST) và quản lý chuỗi cung ứng mã nguồn.',
          focusAreas: ['Threat Modeling (STRIDE)', 'SAST/DAST Integration', 'Software Supply Chain Security (SBOM)', 'Zero Trust Architecture'],
        },
      ],
    },
    lifecycle: {
      sectionDescription:
        'Vòng đời phát triển và tiến hóa phần mềm hiện đại theo tinh thần lặp liên tục, kết hợp giữa kỹ nghệ yêu cầu nghiêm ngặt và tự động hóa DevOps.',
      disclaimer:
        'Lưu ý: Sơ đồ dưới đây phản ánh phương pháp luận kỹ nghệ phần mềm tổng quát, không phải quy trình bắt buộc cho mọi dự án tại Smart Lab.',
      phases: [
        {
          step: '01',
          name: 'Phân tích & Đặc tả yêu cầu',
          englishName: 'Requirements Engineering',
          objective: 'Làm rõ nhu cầu nghiệp vụ và chuyển hóa thành đặc tả kỹ thuật có thể kiểm chứng được.',
          activities: 'Phỏng vấn stakeholder, viết user stories, xác định SLA/SLO về hiệu năng, thiết lập ma trận truy vết yêu cầu.',
          deliverables: 'Đặc tả yêu cầu phần mềm (SRS), User Journey Maps.',
        },
        {
          step: '02',
          name: 'Thiết kế kiến trúc & Schema',
          englishName: 'Architectural & Detailed Design',
          objective: 'Xác định phong cách kiến trúc, phân chia ranh giới dịch vụ và cấu trúc dữ liệu.',
          activities: 'Thiết kế mô hình dữ liệu quan hệ/phi quan hệ, định nghĩa REST/gRPC API contract, đánh giá tính sẵn sàng và khả năng chịu tải.',
          deliverables: 'Tài liệu kiến trúc (ADR), Sơ đồ C4 Model, OpenAPI Schemas.',
        },
        {
          step: '03',
          name: 'Hiện thực hóa & Tái cấu trúc',
          englishName: 'Implementation & Construction',
          objective: 'Chuyển hóa thiết kế thành mã nguồn chất lượng cao, tuân thủ nguyên lý SOLID.',
          activities: 'Lập trình tính năng, viết Unit Tests song song, tiến hành Peer Code Review, chạy công cụ quét tĩnh (Linter, SonarQube).',
          deliverables: 'Mã nguồn đã qua review, Unit test suite đạt độ phủ cao.',
        },
        {
          step: '04',
          name: 'Xác minh & Kiểm thử tự động',
          englishName: 'Verification & Automated Testing',
          objective: 'Kiểm chứng tính đúng đắn và độ bền bỉ của hệ thống trước khi triển khai.',
          activities: 'Chạy integration test, kiểm thử hợp đồng (contract test), kiểm thử tải (load testing) và kiểm thử bảo mật tự động.',
          deliverables: 'Báo cáo kiểm thử tự động, kết quả quét lỗ hổng bảo mật.',
        },
        {
          step: '05',
          name: 'Phát hành liên tục & Triển khai',
          englishName: 'Continuous Delivery & Release',
          objective: 'Đưa phiên bản mới lên môi trường production an toàn và không gây gián đoạn dịch vụ.',
          activities: 'Tự động đóng gói container, triển khai chiến lược Blue/Green hoặc Canary, di chuyển schema cơ sở dữ liệu tự động.',
          deliverables: 'Bản phát hành container hóa, changelog phiên bản.',
        },
        {
          step: '06',
          name: 'Vận hành, Quan sát & Tiến hóa',
          englishName: 'Operations, Observability & Evolution',
          objective: 'Duy trì hoạt động ổn định, phân tích nhật ký và cải tiến hệ thống liên tục.',
          activities: 'Theo dõi dashboard APM/Prometheus, quản lý log tập trung, phân tích RCA khi có sự cố và tái cấu trúc kiểm soát nợ kỹ thuật.',
          deliverables: 'Hệ thống giám sát thời gian thực, kế hoạch cập nhật kỹ thuật.',
        },
      ],
    },
    foundations: {
      sectionDescription:
        'Các nền tảng cốt lõi của khoa học máy tính và kỹ nghệ giúp tạo dựng tư duy thiết kế phần mềm bền vững và chuẩn xác.',
      items: [
        {
          index: '01',
          title: 'Ngôn ngữ lập trình & Hệ thống kiểu',
          englishTitle: 'Programming Languages & Type Systems',
          description: 'Hệ thống kiểu tĩnh vs động, lập trình hàm (functional) vs hướng đối tượng (OOP), quản lý bộ nhớ và đồng thời (concurrency models).',
          disciplines: ['Type Safety & Generics', 'Memory Management Models', 'Concurrency & Asynchrony', 'Functional Paradigms'],
        },
        {
          index: '02',
          title: 'Cấu trúc dữ liệu & Giải thuật nâng cao',
          englishTitle: 'Advanced Data Structures & Algorithms',
          description: 'Cây cân bằng, bảng băm, đồ thị, thuật toán tìm kiếm tối ưu, xử lý chuỗi và phân tích độ phức tạp không gian / thời gian.',
          disciplines: ['Big-O Asymptotic Analysis', 'Graph Algorithms', 'Dynamic Programming', 'Lock-Free Data Structures'],
        },
        {
          index: '03',
          title: 'Kiến trúc máy tính & Hệ điều hành',
          englishTitle: 'Computer Architecture & OS Internals',
          description: 'Tiến trình (processes), luồng (threads), bộ nhớ ảo (virtual memory), giao tiếp IPC, I/O không khóa và cơ chế cache CPU.',
          disciplines: ['Thread Synchronization', 'Virtual Memory & Paging', 'Non-blocking I/O (epoll/kqueue)', 'CPU Cache Locality'],
        },
        {
          index: '04',
          title: 'Mạng máy tính & Hệ thống phân tán',
          englishTitle: 'Networking & Distributed Systems',
          description: 'Giao thức mạng TCP/IP, HTTP/2, HTTP/3, gRPC, định lý CAP, sự đồng thuận phân tán (Raft, Paxos) và kiến trúc microservices.',
          disciplines: ['CAP Theorem & PACELC', 'Consensus Algorithms (Raft)', 'Network Protocols & Sockets', 'Eventual Consistency'],
        },
        {
          index: '05',
          title: 'Cơ sở dữ liệu & Mô hình lưu trữ',
          englishTitle: 'Databases & Storage Engine Design',
          description: 'Mô hình hóa dữ liệu quan hệ, ACID transactions, chỉ mục B-Tree / LSM-Tree, sharding, replication và cơ sở dữ liệu NoSQL/NewSQL.',
          disciplines: ['Relational Normalization', 'Index Data Structures (B-Trees)', 'Transaction Isolation Levels', 'Distributed Partitioning'],
        },
        {
          index: '06',
          title: 'Đảm bảo chất lượng & Phương pháp hình thức',
          englishTitle: 'Software Quality & Formal Verification',
          description: 'Lý thuyết kiểm thử phần mềm, độ bao phủ mã, phân tích tĩnh (static analysis), thiết kế theo hợp đồng (Design by Contract) và thẩm tra hình thức.',
          disciplines: ['Test Coverage Criteria', 'Static & Dynamic Analysis', 'Equivalence Partitioning', 'Code Smell Detection'],
        },
      ],
    },
    responsibility: {
      title: 'Kỷ luật kỹ nghệ, An ninh & Chất lượng phần mềm',
      framework: 'Tham chiếu IEEE Computer Society SWEBOK Guide V4.0a',
      description:
        'Kỹ nghệ phần mềm thực thụ phân biệt với việc viết mã tự do bằng cam kết kỷ luật về chất lượng, an ninh thông tin và trách nhiệm với hệ thống vận hành thực tế.',
      pillars: [
        {
          title: 'Bảo mật theo thiết kế',
          englishTitle: 'Security by Design',
          description: 'Không xem an ninh là bước vá lỗi cuối cùng; chủ động mô hình hóa nguy cơ (Threat Modeling) và áp dụng nguyên tắc đặc quyền tối thiểu (Least Privilege) ngay từ thiết kế.',
        },
        {
          title: 'Khả năng kiểm thử & Xác minh',
          englishTitle: 'Testability & Deterministic Verification',
          description: 'Mọi module phải được thiết kế với tính năng có thể kiểm thử độc lập; hạn chế tối đa mã nguồn ẩn chứa tác dụng phụ (side-effects) không đoán trước.',
        },
        {
          title: 'Kiểm soát nợ kỹ thuật',
          englishTitle: 'Technical Debt Governance',
          description: 'Chủ động đo lường và lên kế hoạch tái cấu trúc định kỳ; không đánh đổi tính bền vững lâu dài của kiến trúc lấy giải pháp vá víu tạm thời.',
        },
        {
          title: 'Khả năng quan sát & Phục hồi',
          englishTitle: 'Observability & Fault Tolerance',
          description: 'Hệ thống phần mềm được thiết kế với cơ chế giám sát toàn diện, ngắt mạch (Circuit Breaker) và tự cô lập sự cố để ngăn sập đổ dây chuyền.',
        },
      ],
    },
    references: [
      {
        institution: 'IEEE Computer Society',
        title: 'Guide to the Software Engineering Body of Knowledge (SWEBOK Guide V4.0a)',
        url: 'https://www.computer.org/education/bodies-of-knowledge/software-engineering',
        domain: 'computer.org',
        scope: 'Tập tài liệu chuẩn mực quốc tế của IEEE định hình toàn diện các vùng tri thức và chuẩn mực nghề nghiệp kỹ nghệ phần mềm.',
      },
      {
        institution: 'IEEE Computer Society',
        title: 'SWEBOK Topics and Knowledge Areas Breakdown',
        url: 'https://www.computer.org/education/bodies-of-knowledge/software-engineering/topics',
        domain: 'computer.org',
        scope: 'Chi tiết phân rã 18 vùng tri thức từ yêu cầu, thiết kế, kiểm thử đến quy trình kỹ nghệ và bảo mật phần mềm.',
      },
    ],
  },
}

/**
 * Retrieve curated domain content by research field code.
 * Falls back to null if the code is unrecognized or custom.
 */
export function getResearchFieldDomainContent(code: string): ResearchFieldDomainContent | null {
  const normalized = code.trim().toUpperCase()
  return RESEARCH_FIELD_CONTENTS[normalized] ?? null
}
