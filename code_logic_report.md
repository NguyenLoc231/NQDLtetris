# code_logic_report.md

## Tổng quan hệ thống

- Kiến trúc tổng quát: code tách rõ 3 lớp trách nhiệm:
  - Model: `tetris.model.*` (`Board`, `Tetromino`, `TetrominoFactory`) — chứa trạng thái lưới và luật vật lý/ma trận khối.
  - Engine / Controller: `tetris.engine.*` (`GameEngine`, `GameState` và các trạng thái) — quản lý vòng lặp, cập nhật trạng thái của game, xử lý input và business logic.
  - View: `tetris.ui.*` (`TetrisFxApp`, `BoardRenderer`, `HoldPanelRenderer`) — hiển thị bằng JavaFX, nhận input từ người dùng và chuyển thành `GameAction`.

- Game Loop: `GameEngine.start(GameRenderer)` khởi `AnimationTimer` và dùng hai bộ cộng thời gian (`updateAccumulatorNs`, `renderAccumulatorNs`) để đảm bảo:
  - Gọi `update()` theo `updateIntervalNs` (tốc độ rơi phụ thuộc `level`).
  - Gọi `render()` theo `TARGET_FRAME_INTERVAL_NS` ( ~16.67ms ).
  - Khi có animation (line-clear, swap) engine tạm ngưng xử lý input/update (cờ `lineClearAnimating`, `swapAnimating`).
  (Xem: `src/main/java/tetris/engine/GameEngine.java`)

- Quản lý trạng thái: `GameState` (interface) và các implement `MenuState` / `PlayingState` / `PausedState` / `GameOverState`. `GameEngine.changeState(...)` gọi `exit()` trên trạng thái cũ và `enter()` trên trạng thái mới; mọi input được ủy quyền tới `currentState.handleInput(...)`.
  (Xem: `src/main/java/tetris/engine/state/GameState.java` và các lớp trạng thái)

## Kiến trúc dữ liệu

- Board (ma trận):
  - Lưu trong `int[][] grid` với kích thước `Board.ROWS = 20`, `Board.COLUMNS = 10`.
  - Giá trị `0` = ô trống; giá trị >0 = id khối (map tới tetromino id).
  - `getGridView()` trả về tham chiếu trực tiếp (hiệu năng read-only); `getGridSnapshot()` trả về bản sao sâu.
  (Xem: `src/main/java/tetris/model/Board.java`)

- Quy tắc va chạm (physics rule):
  - `Board.checkCollision(targetX, targetY, targetShape)` duyệt ma trận `targetShape` và kiểm tra:
    - Va chạm biên trái/phải (`boardX < 0 || boardX >= COLUMNS`).
    - Va chạm đáy (`boardY >= ROWS`).
    - Va chạm ô đã bị khóa (`grid[boardY][boardX] != 0`).
    - Các ô có `boardY < 0` (trên vùng hiển thị) được cho phép khi spawn.
  - Hàm này là điểm chuẩn cho mọi quyết định di chuyển/rotate/ghost/lock.

- Lock & line-clear:
  - `Board.lock(tetromino)` ghi các giá trị shape vào `grid` (kiểm tra `isInside`).
  - `Board.getFullLines()` phát hiện hàng đầy; `Board.removeLines(int[] rows)` thực hiện loại bỏ nhiều hàng một lượt bằng thuật toán sao chép (sử dụng mảng `toRemove[]` rồi copy từ dưới lên) — O(H * W).
  - `Board.clearFullLines()` có phiên bản đơn hàng (dịch từng hàng bằng `removeLine`) — kém hiệu quả hơn nếu làm nhiều lần; engine sử dụng `removeLines(...)` trong hoá kết animation.

- Tetromino (đại diện khối):
  - Lưu trong `int[][] shape`, tọa độ `x`, `y`, và `id`.
  - `getShapeRef()` trả về tham chiếu trực tiếp cho các hệ thống cần hiệu năng (render/collision) — không được mutate từ ngoài.
  - Xoay: `rotateClockwise(int[][])` thực hiện transpose + reverse (tạo mảng mới). `rotateCounterClockwise()` cũng tạo mảng mới bằng ánh xạ chỉ số.
  (Xem: `src/main/java/tetris/model/Tetromino.java` và `src/main/java/tetris/model/TetrominoFactory.java`)

## Phân tích Tính năng (Đã thêm vs. Truyền thống)

### Truyền thống (core Tetris)

- Spawn & next: `TetrominoFactory.create(...)` + `GameEngine.spawnRandomTetromino()` chịu trách nhiệm sinh khối và cập nhật `nextTetrominoType`.
- Rơi / gravity: `GameEngine.update()` → `PlayingState.update()` gọi `engine.stepDown()`; `stepDown()` dùng `Board.checkCollision(...)` để quyết định `lock` hoặc `setPosition`.
- Di chuyển: `GameEngine.moveCurrent(dx,dy)` + checkCollision trước khi set position.
- Xoay: `Tetromino.rotateClockwise()` (ma trận rotate) + `GameEngine.rotateCurrentClockwise()` thực hiện rotate và revert nếu va chạm.
- Line clear & scoring: `Board.getFullLines()`, `Board.removeLines()` hoặc `Board.clearFullLines()`, và `GameEngine.calculateScore(clearedLines)`.

### Đã thêm / mở rộng (Implemented extras)

- Ghost Piece (hiện đường rơi tối đa):
  - Tính toán: `GameEngine.getGhostY()` lặp xuống dưới (gọi `checkCollision`) để tìm Y tối đa hợp lệ.
  - Render: `BoardRenderer.drawBoard(...)` vẽ ghost với opacity (`drawTetrominoAt(..., opacity = 0.25)`).

- Hold (swap) piece:
  - Logic: `GameEngine.holdCurrentPiece()` dùng `TetrominoFactory.createFrom(...)` để copy hiện trạng khối, thử các kick đơn giản (dxs = -1,1,-2,2 rồi thử y-1), nếu đặt được thì bắt đầu swap animation (`startSwapAnimation`) rồi commit sau khi animation kết thúc (`finalizeSwapAnimation`).
  - UI: `HoldPanelRenderer` đăng ký `HoldPieceListener` / `NextPieceListener` để cập nhật panel.

- Soft drop & input repeat: Tần suất di chuyển khi giữ phím (soft drop and horizontal repeat) được xử lý trong `TetrisFxApp.startInputLoop()` với các bộ đếm thời gian (`leftHeldNs`, `leftRepeatNs`, v.v.) và gửi `GameAction` tương ứng vào `engine.handleInput(...)`.

- Hard drop:
  - `GameEngine.hardDrop()` sử dụng `getGhostY()` để đặt tetromino xuống vị trí sâu nhất, gọi `Board.lock()`, và sử dụng branch animation nhanh (FAST_LINE_CLEAR_ANIMATION_NS) khi clear xảy ra.

- Line-clear animation và pending rows:
  - `GameEngine.startLineClearAnimation(...)` đánh dấu `linesToClear` và gọi `board.setPendingClearRows(...)` để UI có thể vẽ hiệu ứng flash.
  - `BoardRenderer.drawBoard(...)` kiểm tra `board.hasPendingClear()` và vẽ overlay nhấp nháy cho các hàng chờ xóa.
  - Sau thời gian animation, `finalizeLineClearAnimation()` gọi `board.removeLines(...)` và cộng điểm.

- Swap flash overlay: `startSwapAnimation()` lưu `swapCandidate`, `swapElapsedNs` và `isSwapFlashVisible()` tính trạng thái nhấp nháy; `BoardRenderer` vẽ overlay nếu có.

- Next-piece preview & observers: `GameEngine.notifyNextPieceChanged()` với `NextPieceListener` → `HoldPanelRenderer.onNextPieceChanged(...)`.

- Audio / Music: `SoundManager` (pool `AudioClip` cho rotate/hard-drop, `MediaPlayer` cho nhạc nền theo level).

## Các tính năng đã triển khai (Implemented Features) — mapping class/method

- Game loop & state: `tetris.engine.GameEngine.start(GameRenderer)`, `GameState` implementations (`src/main/java/tetris/engine/state/`).
- Input mapping & repeat handling: `tetris.ui.TetrisFxApp` (`scene.setOnKeyPressed`, `startInputLoop()`, `processHorizontal()`, `processSoftDrop()`).
- Collision detection: `tetris.model.Board.checkCollision(int targetX,int targetY,int[][] targetShape)`.
- Locking blocks: `tetris.model.Board.lock(Tetromino)`.
- Line detection/removal: `tetris.model.Board.getFullLines()`, `removeLines(int[] rows)`, `clearFullLines()`.
- Tetromino representation & rotation: `tetris.model.Tetromino.rotateClockwise()`, `rotateCounterClockwise()`, and helper `rotateClockwise(int[][])`.
- Piece factory & copy: `tetris.model.TetrominoFactory.create(...)`, `createFrom(...)`.
- Spawn / next: `tetris.engine.GameEngine.spawnRandomTetromino()` and `notifyNextPieceChanged(...)`.
- Ghost computation: `tetris.engine.GameEngine.getGhostY()`; rendering in `tetris.ui.BoardRenderer.drawBoard(...)`.
- Hold / swap: `tetris.engine.GameEngine.holdCurrentPiece()`, `startSwapAnimation(...)`, `finalizeSwapAnimation()`; UI: `tetris.ui.HoldPanelRenderer`.
- Hard drop: `tetris.engine.GameEngine.hardDrop()`.
- Line-clear animation orchestration: `tetris.engine.GameEngine.startLineClearAnimation(...)`, `board.setPendingClearRows(...)`, `BoardRenderer` flash overlay.
- Sound: `tetris.engine.SoundManager`.

## Ghi chú về độ phức tạp (tóm tắt)

- `Board.checkCollision(...)`: Time O(R*C) với R×C = kích thước ma trận tetromino (thực tế ≤ 4×4 → hằng số); Space O(1).
- `Tetromino.rotateClockwise(...)`: Time O(R*C), Space O(R*C) (tạo ma trận mới).
- `GameEngine.getGhostY()`: Time O(H * R*C) worst-case (H = cao độ board ~20).
- `Board.removeLines(int[] rows)` (bulk): Time O(H * W) (một pass copy), ưu hơn so với xóa từng hàng liên tiếp.

---
Các tham chiếu chính (một số file quan trọng):

- `src/main/java/tetris/engine/GameEngine.java`
- `src/main/java/tetris/engine/state/PlayingState.java`
- `src/main/java/tetris/model/Board.java`
- `src/main/java/tetris/model/Tetromino.java`
- `src/main/java/tetris/model/TetrominoFactory.java`
- `src/main/java/tetris/ui/BoardRenderer.java`
- `src/main/java/tetris/ui/TetrisFxApp.java`
 
Trích dẫn dòng (chỉ mục) — các phương thức/điểm vào chính:

- `start(GameRenderer)`: [src/main/java/tetris/engine/GameEngine.java](src/main/java/tetris/engine/GameEngine.java#L243)
- `getGhostY()`: [src/main/java/tetris/engine/GameEngine.java](src/main/java/tetris/engine/GameEngine.java#L499)
- `holdCurrentPiece()`: [src/main/java/tetris/engine/GameEngine.java](src/main/java/tetris/engine/GameEngine.java#L405)
- `hardDrop()`: [src/main/java/tetris/engine/GameEngine.java](src/main/java/tetris/engine/GameEngine.java#L351)
- `startLineClearAnimation(...)`: [src/main/java/tetris/engine/GameEngine.java](src/main/java/tetris/engine/GameEngine.java#L574)
- `finalizeLineClearAnimation()`: [src/main/java/tetris/engine/GameEngine.java](src/main/java/tetris/engine/GameEngine.java#L587)
- `board.removeLines(...)` (call site): [src/main/java/tetris/engine/GameEngine.java](src/main/java/tetris/engine/GameEngine.java#L597)

- `checkCollision(...)` (definition): [src/main/java/tetris/model/Board.java](src/main/java/tetris/model/Board.java#L66)
- `lock(Tetromino)`: [src/main/java/tetris/model/Board.java](src/main/java/tetris/model/Board.java#L96)
- `getFullLines()`: [src/main/java/tetris/model/Board.java](src/main/java/tetris/model/Board.java#L138)
- `removeLines(int[] rows)`: [src/main/java/tetris/model/Board.java](src/main/java/tetris/model/Board.java#L156)

- `rotateClockwise()` / helper: [src/main/java/tetris/model/Tetromino.java](src/main/java/tetris/model/Tetromino.java#L54) and [src/main/java/tetris/model/Tetromino.java](src/main/java/tetris/model/Tetromino.java#L84)
- `createFrom(...)` (factory copy): [src/main/java/tetris/model/TetrominoFactory.java](src/main/java/tetris/model/TetrominoFactory.java#L83)

- `drawBoard(...)` (renderer impl): [src/main/java/tetris/ui/BoardRenderer.java](src/main/java/tetris/ui/BoardRenderer.java#L89)
- `GameRenderer.drawBoard(...)` (interface): [src/main/java/tetris/engine/GameRenderer.java](src/main/java/tetris/engine/GameRenderer.java#L12)
- `TetrisFxApp.start(...)` (UI entry + input wiring): [src/main/java/tetris/ui/TetrisFxApp.java](src/main/java/tetris/ui/TetrisFxApp.java#L75)

 Nếu bạn muốn, tôi có thể:
 - Bổ sung các đoạn trích mã (2–3 dòng) kèm chỉ dẫn dòng cụ thể để dễ review.
 - Thêm sơ đồ luồng (Mermaid) minh họa game loop / state transitions.

 ## Ví dụ đoạn mã (trích dẫn)
 
 - Collision check (tương tự `Board.checkCollision(...)`):
 ```java
 public boolean checkCollision(int targetX, int targetY, int[][] shape) {
   int rows = shape.length;
   int cols = shape[0].length;
   for (int r = 0; r < rows; r++) {
     for (int c = 0; c < cols; c++) {
       if (shape[r][c] == 0) continue;
       int boardX = targetX + c;
       int boardY = targetY + r;
       if (boardX < 0 || boardX >= COLUMNS) return true;
       if (boardY >= ROWS) return true;
       if (boardY >= 0 && grid[boardY][boardX] != 0) return true;
     }
   }
   return false;
 }
 ```
 
 - Rotate helper (tương tự `Tetromino.rotateClockwise(int[][])`):
 ```java
 private static int[][] rotateClockwise(int[][] shape) {
   int n = shape.length;
   int m = shape[0].length;
   int[][] res = new int[m][n];
   for (int r = 0; r < n; r++) {
     for (int c = 0; c < m; c++) {
       res[c][n - 1 - r] = shape[r][c];
     }
   }
   return res;
 }
 ```
 
 - Ghost computation (tương tự `GameEngine.getGhostY()`):
 ```java
 public int getGhostY() {
   int gy = current.y;
   while (!board.checkCollision(current.x, gy + 1, current.getShapeRef())) {
     gy++;
   }
   return gy;
 }
 ```
 
 - Hold/swap outline (tương tự `GameEngine.holdCurrentPiece()`):
 ```java
 public void holdCurrentPiece() {
   if (holdUsedThisTurn) return;
   if (held == null) held = TetrominoFactory.createFrom(current);
   else { Tetromino tmp = held; held = TetrominoFactory.createFrom(current); current = TetrominoFactory.createFrom(tmp); }
   startSwapAnimation();
 }
 ```
 
 ## Luồng điều khiển (Mermaid)
 
 ```mermaid
 flowchart TD
   A[User Input (TetrisFxApp)] -->|key events| B(GameEngine)
   B --> C{AnimationTimer tick}
   C -->|updateAccumulator >= interval| D[engine.update()]
   C -->|each frame| E[engine.render()]
   D --> F{currentState}
   F --> G[PlayingState.update()]
   G --> H[stepDown()/move/rotate/checkCollision]
   H --> I[Board.lock()/getFullLines()]
   I --> J[maybe startLineClearAnimation]
   J --> K[Board.removeLines() after animation]
   E --> L[BoardRenderer.present()]
   L --> A
 ```
 
 Kết thúc báo cáo.
