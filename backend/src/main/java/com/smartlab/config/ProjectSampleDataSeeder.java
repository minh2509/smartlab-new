package com.smartlab.config;

import com.smartlab.entity.ProjectEntity;
import com.smartlab.entity.ProjectResearchFieldEntity;
import com.smartlab.entity.ResearchFieldEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.ProjectStatus;
import com.smartlab.enums.ProjectType;
import com.smartlab.repo.ProjectRepository;
import com.smartlab.repo.ProjectResearchFieldRepository;
import com.smartlab.repo.ResearchFieldRepository;
import com.smartlab.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectSampleDataSeeder implements ApplicationRunner {

    private final ProjectRepository projectRepository;
    private final ProjectResearchFieldRepository projectResearchFieldRepository;
    private final ResearchFieldRepository researchFieldRepository;
    private final UserRepository userRepository;

    private static final String[] TECH_COVERS = {
            "https://images.unsplash.com/photo-1485827404703-89b55fcc595e?w=800&auto=format&fit=crop&q=80", // Robot
            "https://images.unsplash.com/photo-1518770660439-4636190af475?w=800&auto=format&fit=crop&q=80", // Chip / Circuit
            "https://images.unsplash.com/photo-1531746790731-6c087fecd65a?w=800&auto=format&fit=crop&q=80", // AI Head
            "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=800&auto=format&fit=crop&q=80", // Matrix / Cyber
            "https://images.unsplash.com/photo-1508873696983-2df5293cb32f?w=800&auto=format&fit=crop&q=80", // Drone
            "https://images.unsplash.com/photo-1558494949-ef010cbdcc31?w=800&auto=format&fit=crop&q=80", // Server
            "https://images.unsplash.com/photo-1551288049-bebda4e38f71?w=800&auto=format&fit=crop&q=80", // Data Chart
            "https://images.unsplash.com/photo-1563770660941-20978e870e26?w=800&auto=format&fit=crop&q=80", // Solar/Energy
            "https://images.unsplash.com/photo-1516321318423-f06f85e504b3?w=800&auto=format&fit=crop&q=80", // Digital interface
            "https://images.unsplash.com/photo-1581092160607-ee22621dd758?w=800&auto=format&fit=crop&q=80", // Engineer / lab
    };

    private static final String[] PROJECT_TOPICS = {
            "Hệ thống Định vị SLAM Đa Cảm Biến cho Robot Vận Tải AGV/AMR",
            "Mô hình Ngôn ngữ Lớn Tiếng Việt Hỗ trợ Tra cứu Y tế Dự phòng (RAG)",
            "Giải pháp Thị giác Máy tính Tự động Kiểm tra Lỗi Bản Mạch In PCB AOI",
            "Mạng Lưới Cảm Biến Năng Lượng Thấp LoRaWAN Giám Sát Môi Trường Nước",
            "Hệ thống Phát hiện Xâm nhập Mạng Dựa trên Học Sâu và Phân tích Lưu lượng",
            "Nền tảng Digital Twin Tối ưu Hóa Vận Hành Kho Bãi Logistics",
            "Thiết bị Đo Đạc và Cảnh Báo Khí Độc Hầm Lò Sử dụng Mạng Mesh Zigbee",
            "Mô hình Học Tăng Cường Điều Khiển Cánh Tay Robot 6 Bậc Tự Do",
            "Hệ thống Quản lý và Tự Động Hóa Tòa Nhà Thông Minh Tiết Kiệm Năng Lượng",
            "Nền tảng Blockchain Truy xuất Nguồn gốc Nông Sản Chuỗi Cung Ứng",
            "Hệ thống Drone Tự hành Kiểm tra Vết Nứt Bề Mặt Cầu Đường Bộ",
            "Mô hình Phân tích Điện Não Đồ EEG Hỗ trợ Điều khiển Thiết bị Ngoại vi",
            "Giải pháp Tối ưu Hóa Tuyến Đường Giao Hàng Bằng Thuật Toán Di Truyền",
            "Hệ thống Cảnh báo Sớm Sạt Lở Đất Vùng Đồi Núi qua Cảm Biến Gia Tốc",
            "Nền tảng Microservices Phân tán Hiệu Năng Cao Cho Hệ Thống Smart Lab"
    };

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        long currentCount = projectRepository.count();
        if (currentCount >= 80) {
            log.info("Project database already has {} projects. Skipping sample seeder.", currentCount);
            return;
        }

        log.info("Seeding 100 sample projects across 2017-2026...");
        List<ResearchFieldEntity> fields = researchFieldRepository.findAll();
        List<UserEntity> users = userRepository.findAll();
        UserEntity defaultUser = users.isEmpty() ? null : users.get(0);

        List<ProjectEntity> projectsToSave = new ArrayList<>();
        Random random = new Random(42);

        int projectIndex = (int) currentCount + 1;

        // Years from 2017 to 2026 (10 years, 10 projects each = 100 projects)
        for (int year = 2026; year >= 2017; year--) {
            for (int i = 1; i <= 10; i++) {
                String topicBase = PROJECT_TOPICS[(projectIndex + i) % PROJECT_TOPICS.length];
                String code = String.format("PRJ-%d-%03d", year, i);

                // Ensure unique code
                if (projectRepository.existsByCodeIgnoreCase(code)) {
                    code = String.format("PRJ-%d-X%03d", year, projectIndex);
                }

                String name = topicBase + " (Giai đoạn " + (year - 2016) + ")";
                String desc = "Đề tài nghiên cứu ứng dụng công nghệ chuyên sâu năm " + year + " nhằm giải quyết bài toán tự động hóa, xử lý dữ liệu lớn và tối ưu hóa hệ thống trong thực tiễn.";
                String goal = "Hoàn thiện mô hình thử nghiệm, công bố bài báo khoa học và chuyển giao công nghệ ứng dụng thực tế.";

                ProjectType type = (i % 3 == 0) ? ProjectType.PRODUCTION : ProjectType.RESEARCH;

                // Status distribution
                ProjectStatus status;
                boolean isRecruiting;

                if (year == 2026) {
                    if (i <= 4) {
                        status = ProjectStatus.IN_PROGRESS;
                        isRecruiting = true; // RECRUITING
                    } else if (i <= 7) {
                        status = ProjectStatus.PREPARING;
                        isRecruiting = false; // UPCOMING
                    } else {
                        status = ProjectStatus.IN_PROGRESS;
                        isRecruiting = false; // ACTIVE
                    }
                } else if (year == 2025) {
                    if (i <= 3) {
                        status = ProjectStatus.IN_PROGRESS;
                        isRecruiting = true; // RECRUITING
                    } else if (i <= 6) {
                        status = ProjectStatus.IN_PROGRESS;
                        isRecruiting = false; // ACTIVE
                    } else {
                        status = ProjectStatus.COMPLETED;
                        isRecruiting = false; // COMPLETED
                    }
                } else if (year == 2024) {
                    if (i <= 2) {
                        status = ProjectStatus.IN_PROGRESS;
                        isRecruiting = true; // RECRUITING
                    } else if (i <= 5) {
                        status = ProjectStatus.IN_PROGRESS;
                        isRecruiting = false; // ACTIVE
                    } else {
                        status = ProjectStatus.COMPLETED;
                        isRecruiting = false; // COMPLETED
                    }
                } else {
                    // 2023 down to 2017
                    status = ProjectStatus.COMPLETED;
                    isRecruiting = false;
                }

                LocalDate startDate = LocalDate.of(year, 1 + (i % 11), 1 + (i % 25));
                LocalDate expectedEndDate = startDate.plusMonths(8 + (i % 12));
                LocalDate actualEndDate = (status == ProjectStatus.COMPLETED) ? expectedEndDate.minusDays(5) : null;

                // Assign cover image to ~50% of the projects
                String coverUrl = null;
                if ((i + year) % 2 == 0) {
                    coverUrl = TECH_COVERS[(projectIndex + i) % TECH_COVERS.length];
                }

                UserEntity leader = users.isEmpty() ? null : users.get((projectIndex + i) % users.size());

                ProjectEntity project = ProjectEntity.create(
                        code,
                        name,
                        desc,
                        goal,
                        type,
                        leader,
                        status,
                        startDate,
                        expectedEndDate,
                        actualEndDate,
                        true,
                        i == 1 || i == 5,
                        isRecruiting,
                        defaultUser,
                        coverUrl
                );

                projectsToSave.add(project);
                projectIndex++;
            }
        }

        List<ProjectEntity> savedProjects = projectRepository.saveAll(projectsToSave);

        // Assign research fields to each project
        if (!fields.isEmpty()) {
            List<ProjectResearchFieldEntity> projectFields = new ArrayList<>();
            for (int idx = 0; idx < savedProjects.size(); idx++) {
                ProjectEntity p = savedProjects.get(idx);
                // Assign 1 to 3 fields
                int numFields = 1 + (idx % 3);
                for (int f = 0; f < numFields; f++) {
                    ResearchFieldEntity field = fields.get((idx + f) % fields.size());
                    projectFields.add(ProjectResearchFieldEntity.create(p, field));
                }
            }
            projectResearchFieldRepository.saveAll(projectFields);
        }

        log.info("Successfully seeded 100 projects with research fields across years 2017-2026!");
    }
}
