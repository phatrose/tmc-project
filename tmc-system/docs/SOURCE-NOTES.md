# Nguồn và các điểm còn cần thiết kế

Nguồn mới: [Final](https://docs.google.com/document/d/1m7kE9Fa-oxiN0MfalyYsmn8aXWNKC2DB9pknqK-blbY/edit). Các phương án đã áp dụng được ghi trong [DECISIONS.md](DECISIONS.md); tài liệu này được ưu tiên khi khác PDF gốc.

Đã thống nhất: 5 module Central/2 Edge và phạm vi; lịch 06:30; JWS header/claims và quyền nhà máy bằng policy cache; QR chỉ trên app/web; delivery/ACK; revocation online/offline; Auditor.

Các chi tiết vẫn cần nhóm thiết kế khi hiện thực:

- PDF ghi tổng 46 FR nhưng các bảng liệt kê 49; giữ đủ 49 mã trong REQUIREMENTS.
- Canonical JSON và kiểu ID/wire schema cần thống nhất với lưu trữ. DTO là khung đề xuất, không khẳng định wire format đã hoàn tất.
- Clock skew, key trust window/provisioning phải đáp ứng offline tối thiểu 24 giờ; không tự kéo dài tin cậy khóa hết hạn.
- Replay một lần tại Edge không bảo đảm chống replay toàn cục giữa các Edge offline; cần đối soát.
- Export sau chốt, event đến muộn/mở lại kỳ và phân quyền export audit cần test cụ thể.

Bản [main.pdf](reference/main.pdf) giữ nguyên 106 trang, SHA-256 `c7cdf2a55426587dfa099cda24c0ac1995d23e8ad75c134aa261b413fdc0901a`. Không dùng tên người hoặc số Task trong nguồn làm phân công code; [TEAM.md](TEAM.md) là phân công hiện tại.
