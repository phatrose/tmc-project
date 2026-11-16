# Quy trình Git của nhóm

`main` là nhánh chính chứa bản đã được duyệt, không phải một file. Mỗi người làm trên nhánh Task riêng và gửi Pull Request (PR) về `main`. Phân công chi tiết ở [TEAM.md](docs/TEAM.md).

## Tạo nhánh trước khi làm

Sau khi clone repo và mở terminal tại thư mục có pom.xml:

```sh
git switch main
git pull --ff-only origin main
git switch -c task-1/employee-profile
```

Thay số Task và tên việc đúng phần mình. Ví dụ:

| Task | Ví dụ nhánh |
|---|---|
| 1 | task-1/employee-profile |
| 2 | task-2/gate-scan |
| 3 | task-3/attendance-sync |
| 4 | task-4/payroll-export |
| 5 | task-5/gate-configuration |
| 6 | task-6/qr-verifier |
| 7 | task-7/offline-recovery |
| 8 | task-8/system-integration |

Tạo nhánh mới từ main đã cập nhật cho mỗi đầu việc. Không dùng chung nhánh cá nhân với người khác.

## Commit và gửi PR

```sh
git branch --show-current
git status
```

Nếu đang ở main, tạo nhánh Task trước khi commit. Kiểm tra diff và chỉ stage file thuộc việc đang làm:

```sh
git add <duong-dan-file>
git diff --cached
git commit -m "Implement employee profile validation"
git push -u origin HEAD
```

Trên GitHub chọn Compare & pull request, base là main, compare là nhánh vừa push. Điền phạm vi, mã yêu cầu và kết quả kiểm tra. Chờ người khác review; sửa lỗi trên chính nhánh đó rồi push tiếp. Không push/force-push main, không tự bỏ qua review.

Khi main có thay đổi cần lấy về nhánh đang làm, commit hoặc cất thay đổi của mình trước rồi:

```sh
git fetch origin
git merge origin/main
```

Giải quyết xung đột cùng chủ phần liên quan, chạy lại kiểm tra rồi push. Không dùng reset --hard để xử lý xung đột khi còn việc chưa lưu.

## Bảo vệ main trên GitHub (chủ repo thiết lập)

**Trạng thái: chưa xác nhận bật trên GitHub.** File này chỉ hướng dẫn; không tự chặn push.

Settings → Rules → Rulesets: tạo branch ruleset, target chính xác main, Enforcement Active, không cho thành viên thường bypass.

- Require a pull request before merging.
- Require approvals: ít nhất 1 người khác tác giả PR.
- Dismiss stale approvals khi thêm commit; yêu cầu giải quyết review conversations.
- Chặn force push và xóa nhánh main.
- Áp dụng quy tắc cho quản trị viên nếu muốn mọi thay đổi đều qua PR; không để bypass vô tình.
- Chỉ chọn required status checks sau khi workflow thực sự chạy và có tên check đúng.

Nếu muốn trưởng nhóm bắt buộc duyệt, cần CODEOWNERS ghi tài khoản thật của người có quyền phù hợp và bật Require review from Code Owners; chỉ có file CODEOWNERS là chưa đủ.

Chỉ chủ repo/người quản trị cần quyền quản trị. Không chia sẻ tài khoản hoặc token. Với repo thuộc tài khoản cá nhân, quyền collaborator khác mô hình vai trò Write/Admin của organization; kiểm tra cấu hình thực tế thay vì giả định có cùng các lựa chọn.

GitHub Free hỗ trợ protected branches/rulesets trên repo Public; repo Private cần gói có hỗ trợ. Không tự chuyển repo sang Public chỉ để bật bảo vệ. Tài liệu: https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches

## Kiểm tra trước PR

- Tôn trọng phân công và contract; báo các file chung bị ảnh hưởng.
- Không commit target, node_modules, dist, cache, database cá nhân hoặc secret.
- Java: mvn compile; frontend: npm ci và npm run build.
- Khi hiện thực nghiệp vụ, thêm/chạy test phù hợp; ghi rõ test chưa chạy hoặc chức năng còn TODO.
- Không ghi PASS nghiệp vụ chỉ vì sườn compile thành công.
