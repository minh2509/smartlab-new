-- ----------------------------------------------------------------------------
-- 017_seed_100_projects.sql
-- Thêm 100 đề tài nghiên cứu & ứng dụng trải dài từ năm 2017 đến năm 2026
-- ----------------------------------------------------------------------------

-- Đảm bảo có cột cover_url trong bảng projects nếu chưa có
ALTER TABLE projects ADD COLUMN IF NOT EXISTS cover_url VARCHAR(500);

-- Tạo 100 dự án với dữ liệu phong phú theo năm
DO $$
DECLARE
    yr INT;
    idx INT;
    p_id BIGINT;
    p_code VARCHAR(60);
    p_name VARCHAR(200);
    p_desc TEXT;
    p_goal TEXT;
    p_type VARCHAR(20);
    p_status VARCHAR(30);
    p_is_recruiting BOOLEAN;
    p_start_date DATE;
    p_expected_end_date DATE;
    p_actual_end_date DATE;
    p_cover_url VARCHAR(500);
    p_leader_id BIGINT;
    p_created_by_id BIGINT;

    topics VARCHAR[] := ARRAY[
        'Hệ thống Định vị SLAM Đa Cảm Biến cho Robot Vận Tải AGV/AMR',
        'Mô hình Ngôn ngữ Lớn Tiếng Việt Hỗ trợ Tra cứu Y tế Dự phòng (RAG)',
        'Giải pháp Thị giác Máy tính Tự động Kiểm tra Lỗi Bản Mạch In PCB AOI',
        'Mạng Lưới Cảm Biến Năng Lượng Thấp LoRaWAN Giám Sát Môi Trường Nước',
        'Hệ thống Phát hiện Xâm nhập Mạng Dựa trên Học Sâu và Phân tích Lưu lượng',
        'Nền tảng Digital Twin Tối ưu Hóa Vận Hành Kho Bãi Logistics',
        'Thiết bị Đo Đạc và Cảnh Báo Khí Độc Hầm Lò Sử dụng Mạng Mesh Zigbee',
        'Mô hình Học Tăng Cường Điều Khiển Cánh Tay Robot 6 Bậc Tự Do',
        'Hệ thống Quản lý và Tự Động Hóa Tòa Nhà Thông Minh Tiết Kiệm Năng Lượng',
        'Nền tảng Blockchain Truy xuất Nguồn gốc Nông Sản Chuỗi Cung Ứng',
        'Hệ thống Drone Tự hành Kiểm tra Vết Nứt Bề Mặt Cầu Đường Bộ',
        'Mô hình Phân tích Điện Não Đồ EEG Hỗ trợ Điều khiển Thiết bị Ngoại vi',
        'Giải pháp Tối ưu Hóa Tuyến Đường Giao Hàng Bằng Thuật Toán Di Truyền',
        'Hệ thống Cảnh báo Sớm Sạt Lở Đất Vùng Đồi Núi qua Cảm Biến Gia Tốc',
        'Nền tảng Microservices Phân tán Hiệu Năng Cao Cho Hệ Thống Smart Lab'
    ];

    covers VARCHAR[] := ARRAY[
        'https://images.unsplash.com/photo-1485827404703-89b55fcc595e?w=800&auto=format&fit=crop&q=80',
        'https://images.unsplash.com/photo-1518770660439-4636190af475?w=800&auto=format&fit=crop&q=80',
        'https://images.unsplash.com/photo-1531746790731-6c087fecd65a?w=800&auto=format&fit=crop&q=80',
        'https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=800&auto=format&fit=crop&q=80',
        'https://images.unsplash.com/photo-1508873696983-2df5293cb32f?w=800&auto=format&fit=crop&q=80',
        'https://images.unsplash.com/photo-1558494949-ef010cbdcc31?w=800&auto=format&fit=crop&q=80',
        'https://images.unsplash.com/photo-1551288049-bebda4e38f71?w=800&auto=format&fit=crop&q=80',
        'https://images.unsplash.com/photo-1563770660941-20978e870e26?w=800&auto=format&fit=crop&q=80',
        'https://images.unsplash.com/photo-1516321318423-f06f85e504b3?w=800&auto=format&fit=crop&q=80',
        'https://images.unsplash.com/photo-1581092160607-ee22621dd758?w=800&auto=format&fit=crop&q=80'
    ];
BEGIN
    SELECT id INTO p_leader_id FROM tbl_user WHERE is_active = true ORDER BY id ASC LIMIT 1;
    IF p_leader_id IS NULL THEN
        p_leader_id := 1;
    END IF;
    p_created_by_id := p_leader_id;

    FOR yr IN REVERSE 2026..2017 LOOP
        FOR idx IN 1..10 LOOP
            p_code := 'PRJ-' || yr || '-' || LPAD(idx::TEXT, 3, '0');
            
            -- Tránh trùng lặp code
            IF NOT EXISTS (SELECT 1 FROM projects WHERE LOWER(code) = LOWER(p_code)) THEN
                p_name := topics[((yr + idx) % array_length(topics, 1)) + 1] || ' (Niên khóa ' || yr || ')';
                p_desc := 'Đề tài nghiên cứu khoa học và phát triển sản phẩm công nghệ trọng điểm năm ' || yr || ' tại Smart Lab.';
                p_goal := 'Phát triển mô hình thực nghiệm, công bố kết quả khoa học và chuyển giao ứng dụng vào thực tiễn.';
                
                IF idx % 3 = 0 THEN
                    p_type := 'PRODUCTION';
                ELSE
                    p_type := 'RESEARCH';
                END IF;

                -- Phân bổ trạng thái
                IF yr = 2026 THEN
                    IF idx <= 4 THEN
                        p_status := 'IN_PROGRESS';
                        p_is_recruiting := TRUE; -- Đang tuyển
                    ELSIF idx <= 7 THEN
                        p_status := 'PREPARING';
                        p_is_recruiting := FALSE; -- Sắp triển khai
                    ELSE
                        p_status := 'IN_PROGRESS';
                        p_is_recruiting := FALSE; -- Đang thực hiện
                    END IF;
                ELSIF yr = 2025 THEN
                    IF idx <= 3 THEN
                        p_status := 'IN_PROGRESS';
                        p_is_recruiting := TRUE;
                    ELSIF idx <= 6 THEN
                        p_status := 'IN_PROGRESS';
                        p_is_recruiting := FALSE;
                    ELSE
                        p_status := 'COMPLETED';
                        p_is_recruiting := FALSE;
                    END IF;
                ELSIF yr = 2024 THEN
                    IF idx <= 2 THEN
                        p_status := 'IN_PROGRESS';
                        p_is_recruiting := TRUE;
                    ELSIF idx <= 4 THEN
                        p_status := 'IN_PROGRESS';
                        p_is_recruiting := FALSE;
                    ELSE
                        p_status := 'COMPLETED';
                        p_is_recruiting := FALSE;
                    END IF;
                ELSE
                    p_status := 'COMPLETED';
                    p_is_recruiting := FALSE;
                END IF;

                p_start_date := (yr || '-' || LPAD((1 + (idx % 11))::TEXT, 2, '0') || '-10')::DATE;
                p_expected_end_date := p_start_date + INTERVAL '8 months';
                IF p_status = 'COMPLETED' THEN
                    p_actual_end_date := p_expected_end_date - INTERVAL '5 days';
                ELSE
                    p_actual_end_date := NULL;
                END IF;

                -- Ảnh cover cho khoảng 50% dự án
                IF (yr + idx) % 2 = 0 THEN
                    p_cover_url := covers[((yr + idx) % array_length(covers, 1)) + 1];
                ELSE
                    p_cover_url := NULL;
                END IF;

                INSERT INTO projects (
                    code, name, description, goal, project_type, leader_user_id, status,
                    start_date, expected_end_date, actual_end_date, is_public, is_featured, is_recruiting,
                    created_by_user_id, cover_url, created_at, updated_at
                ) VALUES (
                    p_code, p_name, p_desc, p_goal, p_type, p_leader_id, p_status,
                    p_start_date, p_expected_end_date, p_actual_end_date, TRUE, (idx = 1), p_is_recruiting,
                    p_created_by_id, p_cover_url, now(), now()
                ) RETURNING id INTO p_id;

                -- Gán lĩnh vực nghiên cứu ngẫu nhiên
                INSERT INTO project_research_fields (project_id, field_id)
                SELECT p_id, id FROM research_fields
                WHERE id % 3 = (idx % 3) OR id % 2 = (yr % 2)
                ON CONFLICT DO NOTHING;
            END IF;
        END LOOP;
    END LOOP;
END $$;
