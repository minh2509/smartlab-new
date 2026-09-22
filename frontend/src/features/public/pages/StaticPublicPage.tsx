import {
  BadgeCheck,
  FlaskConical,
  GraduationCap,
  Search,
  Building2,
  MapPin,
  Calendar,
  ArrowRight,
} from "lucide-react";
import type { CSSProperties, ReactNode } from "react";
import { useEffect, useState } from "react";
import { listPosts } from "../../posts/api";
import type { PostFeedItem } from "../../posts/types";
import { listPublicProjects } from "../../projects/api";
import type { PublicProjectSummary } from "../../projects/types";
import { coreValues, operatingSteps, aboutQuickFacts } from "../publicData";
import {
  PublicPostCard,
  PublicProjectCard,
} from "../components/PublicDataCards";
import { PublicPageHead } from "../components/PublicPageHead";
import "./StaticPublicPage.css";

type StaticPublicPageProps = {
  title: string;
  description: string;
  kind: "about" | "contact" | "search";
};

export function StaticPublicPage({
  title,
  description,
  kind,
}: StaticPublicPageProps) {
  return (
    <>
      <PublicPageHead title={title} description={description} />
      {kind === "about" && <AboutContent />}
      {kind === "contact" && <ContactContent />}
      {kind === "search" && <SearchContent />}
    </>
  );
}

function countLabel(value: number | null) {
  return value === null ? "..." : String(value);
}

function AboutContent() {
  const valueIcons = [FlaskConical, GraduationCap, BadgeCheck];
  const factIcons = [Calendar, Building2, MapPin];
  const [projectCount, setProjectCount] = useState<number | null>(null);

  useEffect(() => {
    let active = true;
    void listPublicProjects(0, 1)
      .then((projects) => {
        if (active) {
          setProjectCount(projects.totalElements);
        }
      })
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, []);

  return (
    <div className="about-page">
      {/* Hero */}
      <section className="about-hero">
        <div className="wrap">
          <div className="about-hero-inner">
            <span className="about-badge">Về Smart Lab</span>
            <h1 className="about-hero-title">
              Một phòng Lab của sinh viên, vận hành như một nhóm nghiên cứu thật
            </h1>
            <p className="about-hero-sub">
              Smart Lab được thành lập với mục tiêu tạo môi trường để sinh viên
              làm nghiên cứu và phát triển sản phẩm một cách bài bản. Thành viên
              được đưa vào các nhóm dự án có mục tiêu rõ ràng, có leader dẫn dắt
              và có lịch đánh giá tiến độ định kỳ.
            </p>
          </div>
        </div>
      </section>

      {/* Mission */}
      <section className="about-mission">
        <div className="wrap">
          <div className="about-mission-grid">
            <div className="about-content-col">
              <h2 className="about-mission-heading">
                Vì sao Smart Lab ra đời?
              </h2>
              <div className="about-content-block">
                <h3 className="about-content-heading">Mục tiêu</h3>
                <p className="about-content-text">
                  Mỗi thành viên hướng tới ít nhất một kết quả công khai được:
                  một bài viết, một sản phẩm chạy được, hoặc một bộ dữ liệu mở
                  trong thời gian tham gia Lab.
                </p>
              </div>

              <div className="about-content-block">
                <h3 className="about-content-heading">Định hướng</h3>
                <p className="about-content-text">
                  Ba lĩnh vực trọng tâm là <strong>Trí tuệ nhân tạo</strong>,{" "}
                  <strong>Robotics</strong> và{" "}
                  <strong>Kỹ thuật phần mềm</strong>. Lab ưu tiên các bài toán
                  thực tế trong nước và các sản phẩm có thể sử dụng thật.
                </p>
              </div>
            </div>

            <div className="about-quote-card">
              <p className="about-quote-text">
                &ldquo;Không lý thuyết suông, không sản phẩm nửa vời. Mỗi dự án
                tại Smart Lab đều đi từ bài toán thực tế đến tiêu chuẩn nghiệm
                thu khắt khe của từng lĩnh vực nghiên cứu.&rdquo;
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* Quick facts (Render động từ aboutQuickFacts) */}
      <section className="about-facts-strip">
        <div className="wrap">
          <div
            className="about-section-header"
            style={{ marginBottom: "32px" }}
          >
            <span className="about-section-eyebrow">Về chúng tôi</span>
            <h2 className="about-section-title">Hồ sơ phòng Lab</h2>
          </div>
          <div className="about-facts-row">
            {aboutQuickFacts.map((fact, index) => {
              const IconComponent = factIcons[index] ?? Building2;
              return (
                <div className="about-fact-card" key={fact.label}>
                  <div className="about-fact-icon">
                    <IconComponent size={20} />
                  </div>
                  <span className="about-fact-label">{fact.label}</span>
                  <span className="about-fact-value">{fact.value}</span>
                </div>
              );
            })}
          </div>
        </div>
      </section>

      {/* Impact Section */}
      <section className="about-impact-section">
        <div className="wrap">
          <div className="about-impact-card">
            <div className="about-impact-content">
              <span className="about-section-eyebrow">
                Thực chiến & Đột phá
              </span>
              <h2 className="about-section-title">
                {countLabel(projectCount)}+ Dự án đã triển khai
              </h2>
              <p className="about-impact-desc">
                Mỗi sản phẩm tại Smart Lab đều trải qua quá trình nghiên cứu,
                kiểm chứng và phát triển nghiêm ngặt trước khi ứng dụng vào thực
                tế.
              </p>
            </div>
            <div className="about-impact-action">
              <a href="/du-an" className="about-impact-btn">
                Khám phá kho dự án <ArrowRight size={18} />
              </a>
            </div>
          </div>
        </div>
      </section>

      {/* Core Values */}
      <section className="about-values">
        <div className="wrap">
          <div className="about-section-header">
            <span className="about-section-eyebrow">Giá trị cốt lõi</span>
            <h2 className="about-section-title">Ba điều Lab luôn giữ</h2>
            <p className="about-section-desc">
              Những nguyên tắc này quyết định cách Lab làm việc và cách một
              thành viên được đánh giá.
            </p>
          </div>
          <div className="about-values-grid">
            {coreValues.map((value, index) => {
              const Icon = valueIcons[index] ?? BadgeCheck;
              return (
                <div className="about-value-card" key={value.title}>
                  <div
                    className="about-value-icon"
                    style={{ "--c": value.color } as CSSProperties}
                  >
                    <Icon size={24} />
                  </div>
                  <h3 className="about-value-title">{value.title}</h3>
                  <p className="about-value-desc">{value.description}</p>
                </div>
              );
            })}
          </div>
        </div>
      </section>

      {/* Operating Process */}
      <section className="about-process">
        <div className="wrap">
          <div className="about-section-header">
            <span className="about-section-eyebrow">Quy trình</span>
            <h2 className="about-section-title">Cách Lab vận hành</h2>
            <p className="about-section-desc">
              Từ lúc được cấp tài khoản đến lúc có kết quả, mỗi thành viên đi
              qua bốn bước sau.
            </p>
          </div>
          <div className="about-steps">
            {operatingSteps.map((step, index) => (
              <div className="about-step" key={step.title}>
                <div className="about-step-num">{index + 1}</div>
                <div className="about-step-body">
                  <h4 className="about-step-title">{step.title}</h4>
                  <p className="about-step-text">{step.description}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>
    </div>
  );
}

function ContactContent() {
  return (
    <section className="section">
      <div className="wrap">
        <div className="layout-side">
          <div className="card pad">
            <h2>Gửi lời nhắn cho Smart Lab</h2>
            <p className="muted">
              Form đang để mặc định theo mockup. Khi backend có API liên hệ,
              phần submit sẽ được nối vào.
            </p>
            <div className="form-stack mt-20">
              <label className="field">
                <span>Họ tên</span>
                <input className="input" placeholder="Nguyễn Văn A" />
              </label>
              <label className="field">
                <span>Email</span>
                <input className="input" placeholder="you@example.com" />
              </label>
              <label className="field">
                <span>Nội dung</span>
                <textarea
                  className="textarea"
                  placeholder="Bạn muốn trao đổi với Lab về..."
                />
              </label>
              <button className="btn primary" type="button">
                Gửi liên hệ
              </button>
            </div>
          </div>
          <aside>
            <div className="side-box">
              <h4>Thông tin liên hệ</h4>
              <div className="foot-contact">
                <div>Phòng A3-502, Hoà Lạc</div>
                <div>smartlab@example.edu.vn</div>
                <div>Thứ 2 - Thứ 6, 09:00 - 17:00</div>
              </div>
            </div>
          </aside>
        </div>
      </div>
    </section>
  );
}

function SearchContent() {
  const [query, setQuery] = useState("");
  const [projects, setProjects] = useState<PublicProjectSummary[]>([]);
  const [posts, setPosts] = useState<PostFeedItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);
    void Promise.all([
      listPublicProjects(0, 48, { query: query.trim() }),
      listPosts(null, undefined, 30),
    ])
      .then(([projectResult, postResult]) => {
        if (!active) return;
        setProjects(projectResult.items);
        setPosts(postResult.items);
      })
      .catch((reason: unknown) => {
        if (active)
          setError(
            messageOf(reason, "Không tải được dữ liệu tìm kiếm công khai."),
          );
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [query, reloadKey]);

  const normalized = query.trim().toLocaleLowerCase("vi");
  const matchingProjects = projects.filter((project) =>
    includesQuery(
      [
        project.code,
        project.name,
        project.description,
        ...project.leaders.map((leader) => leader.name),
      ],
      normalized,
    ),
  );
  const matchingPosts = posts.filter((post) =>
    includesQuery(
      [post.title, post.excerpt, post.author?.name, post.category?.name],
      normalized,
    ),
  );
  const total = matchingProjects.length + matchingPosts.length;

  return (
    <section className="section">
      <div className="wrap">
        <div className="toolbar">
          <div className="searchbar" style={{ flex: 1, minWidth: 220 }}>
            <Search aria-hidden="true" />
            <input
              className="input"
              type="search"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Tìm dự án, bài viết..."
              aria-label="Tìm nội dung công khai"
              autoFocus
            />
          </div>
        </div>
        {loading && (
          <div className="public-empty empty tight">
            Đang tải dữ liệu tìm kiếm...
          </div>
        )}
        {error && (
          <LoadError
            message={error}
            onRetry={() => setReloadKey((value) => value + 1)}
          />
        )}
        {!loading && !error && total === 0 && (
          <div className="public-empty empty tight">
            Không tìm thấy nội dung phù hợp.
          </div>
        )}
        {!loading && !error && matchingProjects.length > 0 && (
          <SearchGroup title={`Dự án (${matchingProjects.length})`}>
            <div className="grid c3">
              {matchingProjects.map((project) => (
                <PublicProjectCard key={project.id} project={project} />
              ))}
            </div>
          </SearchGroup>
        )}
        {!loading && !error && matchingPosts.length > 0 && (
          <SearchGroup title={`Bài viết (${matchingPosts.length})`}>
            <div className="grid c3">
              {matchingPosts.map((post) => (
                <PublicPostCard key={post.id} post={post} />
              ))}
            </div>
          </SearchGroup>
        )}
      </div>
    </section>
  );
}

function SearchGroup({
  title,
  children,
}: {
  title: string;
  children: ReactNode;
}) {
  return (
    <div className="mt-20">
      <h2>{title}</h2>
      {children}
    </div>
  );
}

function LoadError({
  message,
  onRetry,
}: {
  message: string;
  onRetry: () => void;
}) {
  return (
    <div role="alert">
      <div className="alert error">{message}</div>
      <button className="btn" type="button" onClick={onRetry}>
        Thử tải lại
      </button>
    </div>
  );
}

function includesQuery(
  values: Array<string | null | undefined>,
  normalizedQuery: string,
) {
  if (!normalizedQuery) return true;
  return values.some((value) =>
    value?.toLocaleLowerCase("vi").includes(normalizedQuery),
  );
}

function messageOf(reason: unknown, fallback: string) {
  return reason instanceof Error ? reason.message : fallback;
}