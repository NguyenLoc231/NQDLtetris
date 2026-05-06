# BÁO CÁO CƠ CHẾ GAME TETRIS / TETRIS GAME MECHANISM REPORT

## 1) Tổng quan kiến trúc / Architecture Overview
**[VN]** Game được tổ chức theo hướng tách 3 lớp (MVC):
- `model`: Dữ liệu game (`Board`, `Tetromino`, `TetrominoFactory`)
- `engine`: Logic và State Machine (`GameEngine`, các `GameState`)
- `ui`: Trình xuất hình ảnh (Render) và xử lý đầu vào (Input) bằng JavaFX (`TetrisFxAppExample`, `BoardRenderer`, `HoldPanelRenderer`)
*Mục tiêu:* Model không phụ thuộc UI; Engine không phụ thuộc JavaFX; UI chỉ làm việc hiển thị và chuyển input thành `GameAction`.

**[EN]** The game is structured into 3 distinct layers (MVC):
- `model`: Game data (`Board`, `Tetromino`, `TetrominoFactory`)
- `engine`: Logic and State Machine (`GameEngine`, `GameState` classes)
- `ui`: JavaFX rendering and input handling (`TetrisFxAppExample`, `BoardRenderer`, `HoldPanelRenderer`)
*Objective:* The Model is UI-independent; the Engine is JavaFX-independent; the UI strictly handles rendering and mapping inputs to `GameAction`.

## 2) Vòng đời chạy game / Game Lifecycle

### 2.1 Khởi tạo / Initialization
**[VN]** 
1. Tạo `GameEngine`, `BoardRenderer`, và `HoldPanelRenderer`.
2. Đăng ký listener (bộ lắng nghe) cho khối gạch Hold/Next vào Engine.
3. Hiển thị Overlay chọn ngôn ngữ -> Chọn Level.
4. Khi chọn Level: Set level, reset game, chuyển state sang `PLAYING`, bắt đầu Game Loop và Input Loop.

**[EN]** 
1. Initialize `GameEngine`, `BoardRenderer`, and `HoldPanelRenderer`.
2. Register Hold/Next piece listeners to the engine.
3. Display Language Selection Overlay -> Level Selection Overlay.
4. Upon Level selection: Set level, reset game, transition to `PLAYING` state, start Game Loop and Input Loop.

### 2.2 Game loop chính / Main Game Loop (Engine)
**[VN]** `GameEngine` dùng `AnimationTimer` với 2 bộ tích lũy (accumulator):
- `updateAccumulatorNs`: Tích lũy để gọi `update()` theo tốc độ rơi.
- `renderAccumulatorNs`: Tích lũy để render ở mức ~60 FPS.
*Tính năng:* Tốc độ rơi tăng theo level; Khóa input/update khi đang có hiệu ứng (animation) xóa hàng hoặc đổi gạch (swap hold).

**[EN]** `GameEngine` utilizes `AnimationTimer` with 2 accumulators:
- `updateAccumulatorNs`: Accumulates time to call `update()` based on falling speed.
- `renderAccumulatorNs`: Accumulates time to render at ~60 FPS.
*Features:* Falling speed increases with level; Input/update is locked during line clear or hold swap animations.

## 3) State Machine
**[VN]** Game sử dụng State Pattern với 4 trạng thái: `MenuState`, `PlayingState`, `PausedState`, `GameOverState`.
Mỗi state chỉ xử lý những input hợp lệ cho màn hình hiện tại, đảm bảo logic gameplay không chạy ngầm khi đang Pause/Menu/Game Over.

**[EN]** The game implements the State Pattern with 4 states: `MenuState`, `PlayingState`, `PausedState`, `GameOverState`.
Each state only processes valid inputs for the current screen, ensuring gameplay logic is halted during Pause/Menu/Game Over.

## 4) Cơ chế Model / Model Mechanisms

### 4.1 Lưới trò chơi (Board)
**[VN]** Là ma trận `20 x 10` (`grid[row][col]`). Số 0 là ô trống, 1..7 là ID màu của block đã khóa (locked). Xử lý va chạm (`checkCollision`), khóa gạch (`lock`), và xóa hàng (`removeLines`). Hỗ trợ API cho hiệu ứng chớp nháy trước khi xóa thật (`pendingClearRows`).

**[EN]** A `20 x 10` matrix (`grid[row][col]`). 0 represents an empty cell, 1..7 are color IDs of locked blocks. Handles collisions (`checkCollision`), piece locking (`lock`), and line clearing (`removeLines`). Supports APIs for pre-clear flash animations (`pendingClearRows`).

### 4.2 Tetromino & Factory
**[VN]** Lớp `TetrominoFactory` tạo ngẫu nhiên khối gạch mới, map ID với `TetrominoType`, và copy gạch để phục vụ tính năng Hold/Swap.

**[EN]** `TetrominoFactory` generates random new pieces, maps IDs to `TetrominoType`, and copies pieces to support the Hold/Swap feature.

## 5) Gameplay Logic / Detailed Gameplay Logic
**[VN]** 
- **Rơi & Khóa:** Di chuyển khối gạch xuống (y+1). Nếu va chạm, khóa khối gạch. Nếu có hàng đầy, chạy animation xóa; nếu không, xóa tức thì, cộng điểm và tạo gạch mới.
- **Hard Drop:** Đưa gạch đến vị trí rơi hợp lệ cuối cùng (`ghostY`) và khóa ngay lập tức.
- **Hold/Swap:** Hỗ trợ lưu khối gạch hiện tại và tráo đổi (swap) không giới hạn số lần.
- **Ghost Piece:** Vẽ khối gạch bóng mờ để gợi ý vị trí rơi.

**[EN]** 
- **Fall & Lock:** Moves the piece down (y+1). On collision, the piece is locked. Triggers clear animation if lines are full; otherwise, clears immediately, adds score, and spawns a new piece.
- **Hard Drop:** Drops the piece to the lowest valid position (`ghostY`) and locks it instantly.
- **Hold/Swap:** Supports saving the current piece and unlimited swapping.
- **Ghost Piece:** Renders a semi-transparent piece to indicate the drop location.

## 6) Hệ thống Input / Input System
**[VN]** Chia làm 2 nhóm: One-shot (bấm 1 lần: Enter, Up, Space, P, C...) và Hold-to-repeat (nhấn giữ: Left, Right, Down). Cơ chế giữ phím có độ trễ ban đầu và lặp lại theo chu kỳ để thao tác mượt mà.

**[EN]** Divided into 2 groups: One-shot (single press: Enter, Up, Space, P, C...) and Hold-to-repeat (press & hold: Left, Right, Down). The hold mechanism features an initial delay and cyclic repetition for smooth operation.

## 7) Hệ thống hiển thị / Rendering System
**[VN]** 
- `BoardRenderer`: Vẽ lưới, gạch đã khóa, gạch đang rơi, ghost piece và các hiệu ứng chớp nháy (swap/clear line).
- `HoldPanelRenderer`: Vẽ bảng dự đoán (Next) và gạch đang giữ (Hold).
- Giao diện người dùng (UI Overlay): Dùng `StackPane` của JavaFX để hiển thị linh hoạt các màn hình chọn Ngôn ngữ, Cấp độ, Hướng dẫn và Tạm dừng.

**[EN]** 
- `BoardRenderer`: Renders the grid, locked pieces, active piece, ghost piece, and flash animations.
- `HoldPanelRenderer`: Renders the Next preview and Hold piece panels.
- UI Overlay: Utilizes JavaFX `StackPane` to flexibly toggle Language, Level, Help, and Pause screens.

## 8) Tính điểm & Cấp độ / Scoring & Levels
**[VN]** 
- Điểm: 1 hàng (100), 2 hàng (300), 3 hàng (500), 4 hàng (800).
- Level: Tối đa level 10. Tốc độ rơi tăng dần theo cấp số nhân dựa trên level hiện tại.

**[EN]** 
- Scoring: 1 line (100), 2 lines (300), 3 lines (500), 4 lines (800).
- Levels: Maximum level 10. Falling speed increases exponentially based on the current level.

## 9) Hướng phát triển / Future Extensions
**[VN]** Thêm Wall-kick (SRS) cho xoay gạch; Lock delay, Combo, T-spin; Lưu điểm cao (High score) và đa ngôn ngữ toàn diện.

**[EN]** Implement full SRS Wall-kick for rotation; Lock delay, Combos, T-spins; Save High scores and full comprehensive multi-language support.