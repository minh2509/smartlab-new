import { ArrowRight, CheckCircle2 } from 'lucide-react'
import { Link } from 'react-router-dom'
import { fields, members, posts, projects, stats } from '../publicData'
import { FieldCard, MemberCard, PostCard, ProjectCard } from './StaticPublicPage'

export function HomePage() {
  return (
    <>
      <section className="hero">
        <div className="hero-in">
          <div>
            <h1>
              Nơi sinh viên làm <em>nghiên cứu thật</em>, trên dự án thật.
            </h1>
            <p className="lead">
              Smart Lab là phòng nghiên cứu về Trí tuệ nhân tạo, Robotics và Kỹ thuật phần mềm. Chúng tôi nhận sinh viên
              vào các nhóm dự án có người hướng dẫn, có nhiệm vụ rõ ràng và có đánh giá định kỳ.
            </p>
            <div className="hero-cta">
              <Link className="btn primary lg" to="/du-an">
                <ArrowRight />
                Xem dự án đang chạy
              </Link>
              <Link className="btn outline-light lg" to="/lien-he">
                Liên hệ Smart Lab
              </Link>
            </div>
            <div className="hero-note">
              <CheckCircle2 />
              38 thành viên đang hoạt động · 12 dự án nghiên cứu & sản phẩm
            </div>
          </div>

          <div className="window" aria-hidden="true">
            <div className="window-bar">
              <i />
              <i />
              <i />
              <span>smartlab.fpt.edu.vn / khong-gian-lam-viec</span>
            </div>
            <div className="window-body">
              {projects.slice(0, 4).map((project) => (
                <div className="wrow" key={project.title}>
                  <span
                    className="ava sm"
                    style={{
                      background: project.field === 'AI' ? 'var(--s1)' : project.field === 'Robotics' ? 'var(--s2)' : 'var(--s3)',
                    }}
                  >
                    {project.members[0]}
                  </span>
                  <span className="t">
                    <b>{project.title}</b>
                    <span>
                      {project.field} · {project.members.length} thành viên · {project.status}
                    </span>
                  </span>
                  <span className="meter">
                    <i style={{ width: project.progress }} />
                  </span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      <section className="section">
        <div className="wrap">
          <div className="sec-head">
            <div className="kicker">Định hướng nghiên cứu</div>
            <h2>Ba lĩnh vực chính</h2>
            <p>Mỗi lĩnh vực có nhóm dự án riêng, người hướng dẫn riêng và lộ trình kỹ năng riêng cho thành viên mới.</p>
          </div>
          <div className="grid c3">
            {fields.map((field) => (
              <FieldCard key={field.id} field={field} />
            ))}
          </div>
        </div>
      </section>

      <section className="section alt">
        <div className="wrap">
          <div className="sec-row">
            <div className="sec-head">
              <div className="kicker">Dự án nổi bật</div>
              <h2>Đang thực hiện tại Lab</h2>
              <p>Chỉ hiển thị phần thông tin được công khai. Nội dung nội bộ cần đăng nhập và là thành viên dự án.</p>
            </div>
            <Link className="section-cta" to="/du-an">
              <span>Xem tất cả {stats[1].value} dự án</span>
              <ArrowRight />
            </Link>
          </div>
          <div className="grid c3">
            {projects.slice(0, 3).map((project) => (
              <ProjectCard key={project.title} project={project} />
            ))}
          </div>
        </div>
      </section>

      <section className="section">
        <div className="wrap">
          <div className="sec-row">
            <div className="sec-head">
              <div className="kicker">Con người</div>
              <h2>Thành viên nổi bật</h2>
              <p>Lab vận hành theo mô hình Leader dẫn dắt nhóm dự án, mỗi thành viên có nhiệm vụ và đánh giá riêng.</p>
            </div>
            <Link className="section-cta" to="/thanh-vien">
              <span>Xem toàn bộ thành viên</span>
              <ArrowRight />
            </Link>
          </div>
          <div className="grid c4">
            {members.slice(0, 4).map((member) => (
              <MemberCard key={member.name} member={member} />
            ))}
          </div>
        </div>
      </section>

      <section className="section alt">
        <div className="wrap">
          <div className="sec-row">
            <div className="sec-head">
              <div className="kicker">Bài viết & tin tức</div>
              <h2>Cập nhật mới nhất</h2>
              <p>Thông báo, kết quả nghiên cứu, hướng dẫn kỹ thuật và chia sẻ kinh nghiệm từ các nhóm dự án.</p>
            </div>
            <Link className="section-cta" to="/bai-viet">
              <span>Xem bài viết</span>
              <ArrowRight />
            </Link>
          </div>
          <div className="grid c3">
            {posts.slice(0, 3).map((post) => (
              <PostCard key={post.title} post={post} />
            ))}
          </div>
        </div>
      </section>
    </>
  )
}
