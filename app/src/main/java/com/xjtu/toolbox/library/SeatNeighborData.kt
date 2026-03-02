package com.xjtu.toolbox.library

/**
 * 座位桌组（同桌邻位）数据。
 *
 * 基于各区域实景平面图标注，定义"桌组"——共享同一张物理桌子的座位集合。
 *
 * 精确覆盖区域（来自平面图人工标注）：
 *   - 北楼四层西南侧 (001–136)：画布=17 组×8座；算法用 34 组×4座（每张四人桌独立分组，防止评分虚高）
 *   - 北楼四层东南侧 (001–136)：画布=17 组×8座（镜像）；算法用 34 组×4座
 *   - 北楼四层中间 (J001–J144)：12 组×12座（两张6人桌紧密相接）
 *   - 北楼四层东侧 (F/G/H)：2F+4H=6座/桌, 两桌相接=12 + G独立区
 *   - 北楼四层西侧 (M/K/L)：2K+4L=6座/桌, 两桌相接=12 + M独立区
 *   - 大屏辅学空间 (P001–P018)：四人桌 + 独立工位 + 圆桌
 *   - 南楼二层大厅 (C01–C112)：弧形二人桌 + 长桌 + 散座
 *
 * 不做推荐的区域（座位少，无需邻位推荐）：
 *   - 三楼所有区域 (X/Y/P + 南楼三层中段)
 */
object SeatNeighborData {

    /** 座位面朝方向 */
    enum class FacingDir { NORTH, SOUTH, EAST, WEST, UNKNOWN }

    /**
     * 座位完整空间位置属性。
     *
     * 坐标系 (north4east/west 区域):
     *   gridX: 0 = 入口端(西), 增大 → 出口端(东)
     *   gridY: 0 = 北墙, 增大 → 南墙 (走廊间隙在 gridY ≈ 6)
     *
     * 上行 (北侧): F席 gridY=0,1 (贴北墙); H席 gridY=2-5 (靠走廊)
     * 下行 (南侧): H席 gridY=7-10 (靠走廊); F席 gridY=11,12 (贴南墙)
     */
    /** 距墙某侧距离 → 该侧贡献的靠墙分值 (1.0=直靠, 0.5=隔一排, 0=中间) */
    private fun wallEdgeScore(dist: Int): Float = when (dist) { 0 -> 1f; 1 -> 0.5f; else -> 0f }

    data class SeatPosition(
        val isWallSide: Boolean = false,
        val unitIndexInRow: Int = -1,   // 行内单位编号：0=出口端, rowLength-1=入口端
        val rowLength: Int = 0,
        val rowIndex: Int = -1,         // 物理行：0=北(upper row), 1=南(lower row)
        val facingDir: FacingDir = FacingDir.UNKNOWN,
        val acrossCount: Int = 0,       // 桌子对面的座位数 (F=4, H=2)
        val gridX: Int = -1,            // 抽象列坐标（0=入口侧）
        val gridY: Int = -1,            // 抽象行坐标（0=北墙）
        /** 两端均为入口（如 J 区走廊两头都是门），置 true 时两端均计为靠入口 */
        val isBiEntrance: Boolean = false,
        /**
         * 靠墙综合分值（0.0–约2.0）。
         * 直靠一面墙=1.0；隔一排半分；墙角两面叠加可达2.0+。
         * 用于替代布尔 isWallSide 以支持渐变评分。
         */
        val wallProximityScore: Float = 0f,
    ) {
        /** 角落单位（所在行首或尾）*/
        val isCornerUnit: Boolean
            get() = rowLength > 0 && (unitIndexInRow == 0 || unitIndexInRow == rowLength - 1)
        /** 靠近入口（高端 2 个单位；若 isBiEntrance=true 则两端均算）*/
        val isNearEntrance: Boolean
            get() = rowLength > 0 && (
                unitIndexInRow >= rowLength - 2 ||
                (isBiEntrance && unitIndexInRow <= 1)
            )
        /** 靠走廊（非靠墙且已知位置）*/
        val isCorridorSide: Boolean
            get() = !isWallSide && rowLength > 0
    }

    /**
     * 返回座位的完整空间位置属性。
     *
     * 坐标推导基于 buildEast/buildWest 的号码公式，与验证图渲染坐标对齐。
     * 未覆盖的区域返回默认值（全 false/0/-1）。
     */
    fun getSeatPosition(seatId: String, areaCode: String): SeatPosition {
        val prefix = seatId.takeWhile { it.isLetter() }
        val num    = seatId.dropWhile { it.isLetter() }.toIntOrNull() ?: return SeatPosition()
        return when (areaCode) {

            // ══════════════════════════════════════════════════════
            // 北楼四层东侧 (F/H/G) — 入口在西(左), 出口在东(右)
            // 上行(北): F贴北墙 gridY=0,1; H靠走廊 gridY=2-5
            // 下行(南): H靠走廊 gridY=7-10; F贴南墙 gridY=11,12
            // ══════════════════════════════════════════════════════
            "north4east" -> when (prefix) {
                "F" -> when (num) {
                    in 21..80 -> {        // 上行 F
                        val j       = (num - 21) / 4
                        val mod     = (num - 21) % 4
                        val unitCol = mod / 2
                        val seatRow = mod % 2
                        val wps     = wallEdgeScore(seatRow) + wallEdgeScore(12 - seatRow) +
                                      wallEdgeScore(j) + wallEdgeScore(9 - j)  // 北墙+居西/居东
                        SeatPosition(
                            isWallSide = true, unitIndexInRow = j, rowLength = 10,
                            rowIndex = 0, facingDir = FacingDir.SOUTH, acrossCount = 4,
                            gridX = 2 * (9 - j) + (1 - unitCol),
                            gridY = seatRow,
                            wallProximityScore = wps
                        )
                    }
                    in 81..120 -> {       // 下行 F
                        val j       = (num - 81) / 4
                        val mod     = (num - 81) % 4
                        val unitCol = mod / 2
                        val seatRow = mod % 2
                        val gY      = 11 + seatRow          // gridY=11或12
                        val wps     = wallEdgeScore(gY) + wallEdgeScore(12 - gY) +
                                      wallEdgeScore(j) + wallEdgeScore(9 - j)  // 南墙+居西/居东
                        SeatPosition(
                            isWallSide = true, unitIndexInRow = j, rowLength = 10,
                            rowIndex = 1, facingDir = FacingDir.NORTH, acrossCount = 4,
                            gridX = 2 * (9 - j) + (1 - unitCol),
                            gridY = gY,
                            wallProximityScore = wps
                        )
                    }
                    else -> SeatPosition()
                }
                "H" -> when (num) {
                    in 1..80 -> {         // 上行 H
                        val j       = (num - 1) / 8
                        val mod     = (num - 1) % 8
                        val unitCol = mod / 4
                        val seatRow = mod % 4
                        val gY      = 2 + seatRow               // 2-5
                        // NS墙距离远(gY=2-5)均为0，只东西墙贡献
                        val wps     = wallEdgeScore(j) + wallEdgeScore(9 - j)
                        SeatPosition(
                            isWallSide = false, unitIndexInRow = j, rowLength = 10,
                            rowIndex = 0, facingDir = FacingDir.NORTH, acrossCount = 2,
                            gridX = 2 * (9 - j) + (1 - unitCol),
                            gridY = gY,
                            wallProximityScore = wps
                        )
                    }
                    in 81..160 -> {       // 下行 H
                        val j       = (num - 81) / 8
                        val mod     = (num - 81) % 8
                        val unitCol = mod / 4
                        val seatRow = mod % 4
                        val gY      = 7 + seatRow               // 7-10
                        val wps     = wallEdgeScore(j) + wallEdgeScore(9 - j)
                        SeatPosition(
                            isWallSide = false, unitIndexInRow = j, rowLength = 10,
                            rowIndex = 1, facingDir = FacingDir.SOUTH, acrossCount = 2,
                            gridX = 2 * (9 - j) + (1 - unitCol),
                            gridY = gY,
                            wallProximityScore = wps
                        )
                    }
                    else -> SeatPosition()
                }
                "G" -> {
                    val grpIdx   = (num - 1) / 12
                    val posGrp   = (num - 1) % 12
                    val isUpper  = grpIdx >= 4
                    val wps      = wallEdgeScore(grpIdx) + wallEdgeScore(5 - grpIdx)  // 两端靠墙
                    SeatPosition(
                        isWallSide = false, unitIndexInRow = grpIdx, rowLength = 6,
                        rowIndex = if (isUpper) 0 else 1,
                        facingDir = FacingDir.UNKNOWN, acrossCount = 0,
                        gridX = 20 + grpIdx,
                        gridY = if (isUpper) posGrp else 7 + posGrp,
                        wallProximityScore = wps
                    )
                }
                else -> SeatPosition()
            }

            // ══════════════════════════════════════════════════════
            // 北楼四层西侧 (K/L/M) — 结构与东侧对称
            // K靠墙, L靠走廊
            // ══════════════════════════════════════════════════════
            "north4west" -> when (prefix) {
                "K" -> when (num) {
                    in 1..44 -> {         // 上区 K
                        val j       = (num - 1) / 4
                        val mod     = (num - 1) % 4
                        val unitCol = mod / 2
                        val seatRow = mod % 2
                        val wps     = wallEdgeScore(seatRow) + wallEdgeScore(12 - seatRow) +
                                      wallEdgeScore(j) + wallEdgeScore(10 - j)
                        SeatPosition(
                            isWallSide = true, unitIndexInRow = j, rowLength = 11,
                            rowIndex = 0, facingDir = FacingDir.SOUTH, acrossCount = 4,
                            gridX = 2 * j + (1 - unitCol),
                            gridY = seatRow,
                            wallProximityScore = wps
                        )
                    }
                    in 45..84 -> {        // 下区 K
                        val j       = (num - 45) / 4
                        val mod     = (num - 45) % 4
                        val unitCol = mod / 2
                        val seatRow = mod % 2
                        val gY      = 11 + seatRow
                        val wps     = wallEdgeScore(gY) + wallEdgeScore(12 - gY) +
                                      wallEdgeScore(j) + wallEdgeScore(9 - j)
                        SeatPosition(
                            isWallSide = true, unitIndexInRow = j, rowLength = 10,
                            rowIndex = 1, facingDir = FacingDir.NORTH, acrossCount = 4,
                            gridX = 2 * j + (1 - unitCol),
                            gridY = gY,
                            wallProximityScore = wps
                        )
                    }
                    else -> SeatPosition()
                }
                "L" -> when (num) {
                    in 1..88 -> {         // 上区 L
                        val j       = (num - 1) / 8
                        val mod     = (num - 1) % 8
                        val unitCol = mod / 4
                        val seatRow = mod % 4
                        val wps     = wallEdgeScore(j) + wallEdgeScore(10 - j)  // 只东西墙
                        SeatPosition(
                            isWallSide = false, unitIndexInRow = j, rowLength = 11,
                            rowIndex = 0, facingDir = FacingDir.NORTH, acrossCount = 2,
                            gridX = 2 * j + (1 - unitCol),
                            gridY = 2 + seatRow,
                            wallProximityScore = wps
                        )
                    }
                    in 89..168 -> {       // 下区 L
                        val j       = (num - 89) / 8
                        val mod     = (num - 89) % 8
                        val unitCol = mod / 4
                        val seatRow = mod % 4
                        val wps     = wallEdgeScore(j) + wallEdgeScore(9 - j)
                        SeatPosition(
                            isWallSide = false, unitIndexInRow = j, rowLength = 10,
                            rowIndex = 1, facingDir = FacingDir.SOUTH, acrossCount = 2,
                            gridX = 2 * j + (1 - unitCol),
                            gridY = 7 + seatRow,
                            wallProximityScore = wps
                        )
                    }
                    else -> SeatPosition()
                }
                "M" -> {
                    val grpIdx  = (num - 1) / 12
                    val posGrp  = (num - 1) % 12
                    val isUpper = grpIdx >= 4            // 对称G区：下4组上2组
                    val wps     = wallEdgeScore(grpIdx) + wallEdgeScore(5 - grpIdx)
                    SeatPosition(
                        isWallSide = false, unitIndexInRow = grpIdx, rowLength = 6,
                        rowIndex   = if (isUpper) 0 else 1,   // 修复：缺失 rowIndex 导致邻组惩罚失效
                        facingDir  = FacingDir.UNKNOWN, acrossCount = 0,
                        gridX = 22 + grpIdx, gridY = posGrp,
                        wallProximityScore = wps
                    )
                }
                else -> SeatPosition()
            }

            // ══════════════════════════════════════════════════════
            // 北楼四层中间 (J001-J144) — 走廊两端均有入口
            // ══════════════════════════════════════════════════════
            "north4middle" -> {
                val k = (num - 1) / 12       // 0-11: 0-5=下行, 6-11=上行
                val posInGrp = (num - 1) % 12
                SeatPosition(
                    isWallSide = false, unitIndexInRow = k % 6, rowLength = 6,
                    rowIndex = if (k < 6) 0 else 1,
                    facingDir = if (posInGrp < 6) FacingDir.EAST else FacingDir.WEST,
                    acrossCount = 6,
                    gridX = k % 6, gridY = posInGrp % 6,
                    isBiEntrance = true,   // 走廊两端均为入口
                    wallProximityScore = wallEdgeScore(k % 6) + wallEdgeScore(5 - k % 6)  // 东西尽头靠墙
                )
            }

            // ═══════════════════════════════════════════════════════
            // 北楼四层西南侧 (001-136，API 返回纯数字，无 Q 前缀)
            // 右列 8 组 (001-064): 入口在右列顶部 (top-right)
            // 左列 9 组 (065-136): 136 在顶 (m=0), 065 在底 (m=8)，整列靠西外墙
            // ═══════════════════════════════════════════════════════
            "north4southwest" -> when {
                num in 1..64 -> {   // 右列, 8 组, 入口在顶 → 反向索引使顶部 unitIndex 最大
                    val gIdx = (num - 1) / 8
                    val wps  = wallEdgeScore(gIdx) + wallEdgeScore(7 - gIdx)  // 南北墙
                    SeatPosition(
                        isWallSide     = gIdx == 0 || gIdx == 7,
                        unitIndexInRow = 7 - gIdx,
                        rowLength      = 8,
                        rowIndex       = 1,
                        facingDir      = FacingDir.UNKNOWN, acrossCount = 2,
                        gridX = 1, gridY = gIdx,
                        wallProximityScore = wps
                    )
                }
                num in 65..136 -> {  // 左列, 9 组 — 整列紧贴西外墙(+1)，顶/底角再加北/南墙(1)
                    val gM         = (136 - num) / 8
                    val rem        = (136 - num) % 8
                    // rem=0: d3(b,b-3,b-1,b-2)中的b → col0 row0 (最靠外墙角)
                    // rem=1: b-1 → col0 row1 (外墙侧，背对面)
                    // rem=2: b-2 → col1 row1 (走廊侧)
                    // rem=3: b-3 → col1 row0 (走廊侧)
                    // rem=4~7: 第二张桌，同理 rem=4/5=col0, rem=6/7=col1
                    val isCornerSeat = rem == 0 || rem == 4  // 本组左上角座，紧靠双墙
                    val isOuterCol   = rem == 0 || rem == 1 || rem == 4 || rem == 5  // col0，靠西外墙
                    val wps = wallEdgeScore(gM) + wallEdgeScore(8 - gM) +  // 南北两端面墙
                              1f +                                           // 整列靠西外墙
                              (if (isCornerSeat) 1f else 0f) +             // 双墙实角额外加分
                              (if (isOuterCol) 0.5f else 0f)               // col0 更靠西外墙（半分）
                    SeatPosition(
                        isWallSide     = gM == 0 || gM == 8,
                        unitIndexInRow = 8 - gM,  // 入口在顶部(gM=0 → 高索引=入口端)
                        rowLength      = 9,
                        rowIndex       = 0,
                        facingDir      = FacingDir.UNKNOWN, acrossCount = 2,
                        gridX = 0, gridY = gM,
                        wallProximityScore = wps
                    )
                }
                else -> SeatPosition()
            }

            // ═══════════════════════════════════════════════════════
            // 北楼四层东南侧 (001-136，API 返回纯数字，无 T 前缀)
            // 右列 9 组 (001-072): 入口不在此列，整列靠东外墙
            // 左列 8 组 (073-136): 136 在顶 (m=0)，入口在左列顶部 (top-left)
            // ═══════════════════════════════════════════════════════
            "north4southeast" -> when {
                num in 1..72 -> {   // 右列, 9 组 — 整列紧贴东外墙(+1)，顶/底角再加南/北墙(1)
                    val gIdx = (num - 1) / 8
                    val rem  = (num - 1) % 8
                    // rem=0/1: col0（靠东外墙 1,2,9,10...）; rem=2/3: col1（走廊侧）
                    // rem=4/5: col0（外桌靠墙）; rem=6/7: col1（外桌走廊侧）
                    val isCornerSeat = rem == 0 || rem == 4  // 本组双墙实角
                    val isOuterCol   = rem == 0 || rem == 1 || rem == 4 || rem == 5  // col0，靠东外墙
                    val wps  = wallEdgeScore(gIdx) + wallEdgeScore(8 - gIdx) +  // 南北两端面墙
                               1f +                                              // 整列靠东外墙
                               (if (isCornerSeat) 1f else 0f) +               // 双墙实角额外加分
                               (if (isOuterCol) 0.5f else 0f)                 // col0 更靠东外墙（半分）
                    SeatPosition(
                        isWallSide     = gIdx == 0 || gIdx == 8,
                        unitIndexInRow = gIdx,
                        rowLength      = 9,
                        rowIndex       = 1,
                        facingDir      = FacingDir.UNKNOWN, acrossCount = 2,
                        gridX = 1, gridY = gIdx,
                        wallProximityScore = wps
                    )
                }
                num in 73..136 -> {  // 左列, 8 组, 136 在顶, 入口在左列顶部
                    val gM   = (136 - num) / 8
                    val rem  = (136 - num) % 8
                    // SE左列同SW左列编号规律（buildLeftColumnGroups共用）
                    // rem=0/1: col0（靠墙角 136/135...）; rem=2/3: col1（走廊侧）
                    // rem=4/5: col0（外桌靠墙）; rem=6/7: col1（外桌走廊侧）
                    val isCornerSeat = rem == 0 || rem == 4
                    val isOuterCol   = rem == 0 || rem == 1 || rem == 4 || rem == 5
                    val wps  = wallEdgeScore(gM) + wallEdgeScore(7 - gM) +
                               (if (isCornerSeat) 1f else 0f) +
                               (if (isOuterCol) 0.5f else 0f)
                    SeatPosition(
                        isWallSide     = gM == 0 || gM == 7,
                        unitIndexInRow = 7 - gM,
                        rowLength      = 8,
                        rowIndex       = 0,
                        facingDir      = FacingDir.UNKNOWN, acrossCount = 2,
                        gridX = 0, gridY = gM,
                        wallProximityScore = wps
                    )
                }
                else -> SeatPosition()
            }

            // ── 南楼 — 暂无完整坐标 ──
            "south2" -> SeatPosition()
            else -> SeatPosition()
        }
    }

    /**
     * 返回该区域的原始桌组列表（供画布渲染用），顺序与 buildXxx() 完全一致。
     * 未覆盖区域返回 null。
     * 注：SW/SE 画布用 8 座组以匹配 TwoColumn 渲染；算法用 EXACT_BUILDERS（4 座组）。
     */
    fun getAreaGroups(areaCode: String): List<List<String>>? =
        (CANVAS_BUILDERS[areaCode] ?: EXACT_BUILDERS[areaCode])?.invoke()

    /**
     * 获取给定座位所在桌组（含自身）。
     * @return 同桌座位 ID 列表，或 null（未知座位）。
     */
    fun getTableGroup(seatId: String, areaCode: String): List<String>? {
        // 精确匹配优先
        EXACT_BUILDERS[areaCode]?.invoke()?.let { groups ->
            groups.find { seatId in it }?.let { return it }
        }
        // 启发式回退
        return computeHeuristicGroup(seatId, areaCode)
    }

    /** 同桌邻座（不含自身） */
    fun getNeighborSeats(seatId: String, areaCode: String): List<String> =
        getTableGroup(seatId, areaCode)?.filter { it != seatId } ?: emptyList()

    /**
     * 旁侧邻座（同行旁边坐、不含面对面的对面座位）。
     *
     * 用于 diffusion 过滤：推荐某座后只排除旁边座，对面座可独立被推荐。
     *
     * ── SW/SE 左列（065-136）物理布局（横排面对面）──
     *   内侧桌: row0: rem=0  rem=3  ← 旁边
     *           row1: rem=1  rem=2  ← 对面
     *   走廊桌: row0: rem=4  rem=7 ;  row1: rem=5  rem=6
     *   旁侧对：0↔3, 1↔2, 4↔7, 5↔6
     *
     * ── SE 右列（001-072）物理布局（横排面对面）──
     *   上桌 g[0..3]: col0row0=mod2, col1row0=mod1, col0row1=mod3, col1row1=mod4
     *   下桌 g[4..7]: col0row0=mod6, col1row0=mod5, col0row1=mod7, col1row1=mod0
     *   旁侧对：1↔2, 3↔4, 5↔6, 0↔7
     *
     * ── SW 右列（001-064）物理布局（横排面对面）──
     *   上桌 g[0..3]: col0row0=mod0, col1row0=mod7, col0row1=mod5, col1row1=mod6
     *   下桌 g[4..7]: col0row0=mod4, col1row0=mod3, col0row1=mod1, col1row1=mod2
     *   旁侧对：0↔7, 5↔6, 4↔3, 1↔2
     *
     * 其他区域：退化为完整 getNeighborSeats（保守，不改变现有行为）。
     */
    fun getSideNeighbors(seatId: String, areaCode: String): List<String> {
        val n = seatId.toIntOrNull() ?: return getNeighborSeats(seatId, areaCode)

        when {
            // ── SW/SE 左列（065-136）─────────────────────────
            (areaCode.endsWith("southwest") || areaCode.endsWith("southeast")) && n in 65..136 -> {
                val gM  = (136 - n) / 8
                val rem = (136 - n) % 8
                val sideRem = when (rem) {
                    0 -> 3; 3 -> 0
                    1 -> 2; 2 -> 1
                    4 -> 7; 7 -> 4
                    5 -> 6; 6 -> 5
                    else -> -1
                }
                val sideN = 136 - 8 * gM - sideRem
                return if (sideRem >= 0 && sideN in 65..136) listOf(p3(sideN)) else emptyList()
            }

            // ── SE 右列（001-072）────────────────────────────
            areaCode == "north4southeast" && n in 1..72 -> {
                val mod = n % 8
                // 旁侧配对：1↔2, 3↔4, 5↔6, 0↔7（mod=0表示8k, 对应 d3 的 b+8 座，其旁侧是 b+7=8k-1=n-1）
                val sideN = when (mod) {
                    1 -> n + 1; 2 -> n - 1   // mod1↔mod2
                    3 -> n + 1; 4 -> n - 1   // mod3↔mod4
                    5 -> n + 1; 6 -> n - 1   // mod5↔mod6
                    0 -> n - 1; 7 -> n + 1   // mod0(=8k)↔mod7(=8k-1)
                    else -> -1
                }
                return if (sideN in 1..72) listOf(p3(sideN)) else emptyList()
            }

            // ── SW 右列（001-064）────────────────────────────
            areaCode == "north4southwest" && n in 1..64 -> {
                val mod = n % 8
                // 旁侧配对：0↔7(b+8↔b+7), 5↔6, 4↔3, 1↔2
                // mod=0 表示 n=8k（d3里的b+8），其旁侧是 b+7=8k-1=n-1
                val sideN = when (mod) {
                    0 -> n - 1; 7 -> n + 1   // mod0(b+8)↔mod7(b+7)
                    5 -> n + 1; 6 -> n - 1   // mod5↔mod6
                    4 -> n - 1; 3 -> n + 1   // mod4(b+4)↔mod3(b+3)
                    1 -> n + 1; 2 -> n - 1   // mod1(b+1)↔mod2(b+2)
                    else -> -1
                }
                return if (sideN in 1..64) listOf(p3(sideN)) else emptyList()
            }

            // ── Middle/East/West 12人组（竖排，左右列面对面）────
            // 12人组内：前6=左列，后6=右列，同列上下相邻=旁侧，对面列不排除
            areaCode in setOf("north4middle", "north4east", "north4west") -> {
                val group = getTableGroup(seatId, areaCode) ?: return getNeighborSeats(seatId, areaCode)
                val posInGrp = group.indexOf(seatId)
                if (posInGrp < 0) return getNeighborSeats(seatId, areaCode)
                // 同列：0..5 为左列，6..11 为右列
                val side = group.indices.filter { idx ->
                    idx != posInGrp &&               // 不含自身
                    idx / 6 == posInGrp / 6 &&       // 同列
                    kotlin.math.abs(idx - posInGrp) == 1  // 相邻
                }.map { group[it] }
                return side
            }
        }
        // 其他区域退化为完整邻座（行为与旧版相同）
        return getNeighborSeats(seatId, areaCode)
    }

    // ═══════════════════════════════════════════════════════════
    //  精确桌组数据（7 个区域）
    // ═══════════════════════════════════════════════════════════

    private val EXACT_BUILDERS: Map<String, () -> List<List<String>>> = mapOf(
        "north4southwest" to ::buildSouthwest,
        "north4southeast" to ::buildSoutheast,
        "north4middle"    to ::buildMiddle,
        "north4east"      to ::buildEast,
        "north4west"      to ::buildWest,
        "south2"          to ::buildSouth2,
    )

    /** 画布专用构建器（SW/SE 保持 8 座组，兼容 TwoColumn 渲染，不干预算法分组）*/
    private val CANVAS_BUILDERS: Map<String, () -> List<List<String>>> = mapOf(
        "north4southwest" to ::buildSouthwestCanvas,
        "north4southeast" to ::buildSoutheastCanvas,
    )

    /**
     * 左列公用构建函数（从 136 递减），算法版：4 座分组。
     * SW/SE 两列共用，只有 groupCount 不同（SW=9, SE=8）。
     */
    private fun buildLeftColumnGroups(groupCount: Int): List<List<String>> = buildList {
        repeat(groupCount) { m ->
            val b = 136 - 8 * m
            add(d3(b, b - 3, b - 1, b - 2))
            add(d3(b - 4, b - 7, b - 5, b - 6))
        }
    }

    /** 左列公用构建函数（从 136 递减），画布版：8 座分组（isEightSeat渲染） */
    private fun buildLeftColumnGroupsCanvas(groupCount: Int): List<List<String>> = buildList {
        repeat(groupCount) { m ->
            val b = 136 - 8 * m
            add(d3(b, b - 3, b - 1, b - 2, b - 4, b - 7, b - 5, b - 6))
        }
    }

    /*
     * ── 北楼四层西南侧 (001–136) ──
     *
     * 两列 × 四人桌布局。每张桌子 2 人面对 2 人。
     * 算法版：右列 8 组×4座(001–064)，左列 9 组×4座(065–136)。
     *
     * k=0 右列最前一组: 上桌=[008,005,007,006] 下桌=[004,001,003,002]
     * m=0 左列顶部一组: 上桌=[136,133,135,134] 下桌=[132,129,131,130]
     */
    private fun buildSouthwest(): List<List<String>> {
        val t = mutableListOf<List<String>>()
        // 右列 (001–064)：8 组 × 2 张四人桌（按单桌分组，避免 8 座"大组"导致评分失真）
        for (k in 0..7) {
            val b = 8 * k
            t += d3(b + 8, b + 5, b + 7, b + 6)   // 上桌（入口侧）
            t += d3(b + 4, b + 1, b + 3, b + 2)   // 下桌
        }
        // 左列 (065–136)：9 组 × 2 张四人桌
        t += buildLeftColumnGroups(9)
        return t
    }

    /** 画布专用：保留 8 座组以匹配 TwoColumn 渲染 */
    private fun buildSouthwestCanvas(): List<List<String>> {
        val t = mutableListOf<List<String>>()
        for (k in 0..7) {
            val b = 8 * k
            t += d3(b + 8, b + 5, b + 7, b + 6, b + 4, b + 1, b + 3, b + 2)
        }
        t += buildLeftColumnGroupsCanvas(9)
        return t
    }

    /*
     * ── 北楼四层东南侧 (001–136) ──
     *
     * 与西南侧镜像对称。
     * 右列 9 组 (001–072)，左列 8 组 (073–136)。
     *
     * 每组 8 座（两张四人桌横向相接）：
     *   k=0 → [002,003,001,004,006,007,005,008]
     *   k=1 → [010,011,009,012,014,015,013,016]
     *   ...
     */
    private fun buildSoutheast(): List<List<String>> {
        val t = mutableListOf<List<String>>()
        // 右列 (001–072)：9 组 × 2 张四人桌（按单桌分组，避免 8 座"大组"导致评分失真）
        for (k in 0..8) {
            val b = 8 * k
            t += d3(b + 2, b + 3, b + 1, b + 4)   // 上桌（入口侧）
            t += d3(b + 6, b + 7, b + 5, b + 8)   // 下桌
        }
        // 左列 (073–136)：8 组 × 2 张四人桌
        t += buildLeftColumnGroups(8)
        return t
    }

    /** 画布专用：保留 8 座组以匹配 TwoColumn 渲染 */
    private fun buildSoutheastCanvas(): List<List<String>> {
        val t = mutableListOf<List<String>>()
        for (k in 0..8) {
            val b = 8 * k
            t += d3(b + 2, b + 3, b + 1, b + 4, b + 6, b + 7, b + 5, b + 8)
        }
        t += buildLeftColumnGroupsCanvas(8)
        return t
    }

    /*
     * ── 北楼四层中间 (J001–J144) ──
     *
     * 12 组 × 12 座（每组 = 两张 6 人桌紧密相接）。
     * 内部布局：左列 n+0…n+5，右列 n+6…n+11
     *
     * 下方 6 组 k=0..5 (J001–J072)：座位号从上到下递增
     *   J001 J007   ← 近走廊 (top)
     *   J002 J008
     *   J003 J009
     *   --- 相接 ---
     *   J004 J010
     *   J005 J011
     *   J006 J012   ← 近墙 (bottom)
     *
     * 上方 6 组 k=6..11 (J073–J144)：内部座位镜像编排
     *   J078 J084   ← 近墙 (top)
     *   J077 J083
     *   J076 J082
     *   --- 相接 ---
     *   J075 J081
     *   J074 J080
     *   J073 J079   ← 近走廊 (bottom)
     *
     * 走廊两端均有入口。
     */
    private fun buildMiddle(): List<List<String>> {
        val t = mutableListOf<List<String>>()
        // 下方 6 组: 正序 (n+0 近走廊, n+5 近墙)
        for (k in 0..5) {
            val s = 12 * k + 1
            t += (s..s + 11).map { "J${p3(it)}" }
        }
        // 上方 6 组: 镜像（n+5 近墙=上方, n+0 近走廊=下方）
        // 桌组成员不变，但内部排序镜像：先 n+5..n+0 再 n+11..n+6
        for (k in 6..11) {
            val s = 12 * k + 1
            val left = (s + 5 downTo s).map { "J${p3(it)}" }      // 左列镜像
            val right = (s + 11 downTo s + 6).map { "J${p3(it)}" } // 右列镜像
            t += left + right
        }
        return t
    }

    /*
     * ── 南楼二层大厅 (C01–C112) ──
     *
     * 复杂布局：
     *   - C01–C28：上方左翼弧形二人桌（14 对）
     *   - C29–C42：左侧 14 座长桌
     *   - C43–C48：底部中间 3 对二人桌
     *   - C49–C56：中间区域 4 对二人桌
     *   - C57–C84：上方右翼弧形二人桌（14 对）
     *   - C85–C98：右侧 14 座长桌
     *   - C99–C104：右下 3 对二人桌
     *   - C105–C112：右侧中间 4 对二人桌
     */
    private fun buildSouth2(): List<List<String>> {
        val t = mutableListOf<List<String>>()
        // 上方左翼 C01–C28
        for (i in 0..13) t += c2(2 * i + 1, 2 * i + 2)
        // 左侧长桌 C29–C42
        t += (29..42).map { "C${p2(it)}" }
        // 底部中间 C43–C48
        for (i in 0..2) t += c2(43 + 2 * i, 44 + 2 * i)
        // 中间 C49–C56
        for (i in 0..3) t += c2(49 + 2 * i, 50 + 2 * i)
        // 上方右翼 C57–C84
        for (i in 0..13) t += c2(57 + 2 * i, 58 + 2 * i)
        // 右侧长桌 C85–C98
        t += (85..98).map { "C${p2(it)}" }
        // 右下 C99–C104
        for (i in 0..2) t += c2(99 + 2 * i, 100 + 2 * i)
        // 右侧中间 C105–C112
        for (i in 0..3) t += c2(105 + 2 * i, 106 + 2 * i)
        return t
    }

    // ═══════════════════════════════════════════════════════════
    //  北楼四层东侧 (F/G/H)
    // ═══════════════════════════════════════════════════════════

    /*
     * ── 北楼四层东侧 ──
     *
     * 南北两行，中间一条走廊横穿，左端入口，右端出口。
     * 每列 = 2F(靠墙) + 4H(靠走廊) = 6座/桌，两桌短边相接 = 12座/单位。
     *
     * 上行 (北墙侧), 左→右:
     *   10个F单位 (F059/F057→F023/F021) → 半墙 → 2个G单位 (G049-G072)
     *   列 k (从右数): F(2k+21), F(2k+22), H(4k+1)..H(4k+4)
     *
     * 下行 (南墙侧), 左→右:
     *   10个F单位 (F120/F118→F084/F082) → 半墙 → 4个G单位 (G001-G048)
     *   列 k (从右数): F(2k+81), F(2k+82), H(4k+81)..H(4k+84)
     *
     * G区: G001-G072 = 6对×12 (下4上2)
     */
    private fun buildEast(): List<List<String>> {
        val t = mutableListOf<List<String>>()
        // 上行 F/H: 10对×12
        for (j in 0..9) t += deskPair12('F', 4 * j + 21, 'H', 8 * j + 1)
        // 下行 F/H: 10对×12
        for (j in 0..9) t += deskPair12('F', 4 * j + 81, 'H', 8 * j + 81)
        // G区: 6对×12
        for (j in 0..5) {
            val b = 12 * j + 1
            t += (b..b + 11).map { "G${p3(it)}" }
        }
        return t
    }

    // ═══════════════════════════════════════════════════════════
    //  北楼四层西侧 (M/K/L)
    // ═══════════════════════════════════════════════════════════

    /*
     * ── 北楼四层西侧 ──
     *
     * 布局类似东侧的镜像：
     *   主区：每列 = 2K(靠墙) + 4L(靠走廊) = 6座/桌，两桌短边相接 = 12
     *   M区：独立桌组，每12座连续编号
     *
     * 上区 K/L：22列 → 11组×12
     *   列 k: K(2k+1), K(2k+2), L(4k+1)..L(4k+4)
     *
     * 下区 K/L：20列 → 10组×12
     *   列 k: K(2k+45), K(2k+46), L(4k+89)..L(4k+92)
     *
     * M区：M001-M072 = 6组×12
     */
    private fun buildWest(): List<List<String>> {
        val t = mutableListOf<List<String>>()
        // 上区 K/L: 11组×12
        for (j in 0..10) t += deskPair12('K', 4 * j + 1, 'L', 8 * j + 1)
        // 下区 K/L: 10组×12
        for (j in 0..9) t += deskPair12('K', 4 * j + 45, 'L', 8 * j + 89)
        // M区: 6组×12
        for (j in 0..5) {
            val b = 12 * j + 1
            t += (b..b + 11).map { "M${p3(it)}" }
        }
        return t
    }

    // ═══════════════════════════════════════════════════════════
    //  启发式桌组计算（覆盖剩余区域）
    // ═══════════════════════════════════════════════════════════

    /**
     * 根据座位号编码规律推算桌组。
     */
    private fun computeHeuristicGroup(seatId: String, areaCode: String): List<String>? {
        val prefix = seatId.takeWhile { it.isLetter() }
        val numStr = seatId.dropWhile { it.isLetter() }
        val num = numStr.toIntOrNull() ?: return null
        if (num <= 0) return null
        val padLen = numStr.length
        val gs = resolveGroupSize(prefix, areaCode)
        val start = ((num - 1) / gs) * gs + 1
        return (start until start + gs).map { "$prefix${it.toString().padStart(padLen, '0')}" }
    }

    /** 根据区域 + 前缀确定桌组大小 */
    private fun resolveGroupSize(prefix: String, areaCode: String): Int = when {
        // 其余所有 → 最保守两人组
        else -> 2
    }

    // ═══ 格式化辅助 ═══

    /** 3 位零填充 */
    private fun p3(n: Int): String = n.toString().padStart(3, '0')

    /** 2 位零填充（≥100 自然溢出为 3 位） */
    private fun p2(n: Int): String = n.toString().padStart(2, '0')

    /** 纯数字 3 位零填充桌组 */
    private fun d3(vararg s: Int): List<String> = s.map { p3(it) }

    /** C 前缀 2 位零填充桌组 */
    private fun c2(vararg s: Int): List<String> = s.map { "C${p2(it)}" }

    /**
     * 构建两列相接桌组 (12座)。
     * 每列 = 2个短前缀座 + 4个长前缀座 = 6座，两列相接 = 12座。
     * @param sp 短前缀字符 (F 或 K, 靠墙侧, 每列2座)
     * @param sb 短前缀起始编号
     * @param lp 长前缀字符 (H 或 L, 靠走廊侧, 每列4座)
     * @param lb 长前缀起始编号
     */
    private fun deskPair12(sp: Char, sb: Int, lp: Char, lb: Int): List<String> = listOf(
        "$sp${p3(sb)}", "$sp${p3(sb + 1)}",
        "$lp${p3(lb)}", "$lp${p3(lb + 1)}", "$lp${p3(lb + 2)}", "$lp${p3(lb + 3)}",
        "$sp${p3(sb + 2)}", "$sp${p3(sb + 3)}",
        "$lp${p3(lb + 4)}", "$lp${p3(lb + 5)}", "$lp${p3(lb + 6)}", "$lp${p3(lb + 7)}"
    )
}
