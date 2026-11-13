package com.clashangprpr.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.clashangprpr.app.engine.ActionButton
import com.clashangprpr.app.engine.Assets
import com.clashangprpr.app.engine.Camera2D
import com.clashangprpr.app.engine.SaveStore
import com.clashangprpr.app.engine.Sound
import com.clashangprpr.app.engine.SpriteSheet
import com.clashangprpr.app.engine.Ui
import com.clashangprpr.app.engine.VirtualJoystick
import com.clashangprpr.app.model.*
import com.clashangprpr.app.story.DialogueLine
import com.clashangprpr.app.story.Story
import com.clashangprpr.app.world.Collider
import com.clashangprpr.app.world.OpenWorld
import com.clashangprpr.app.world.Pt
import com.clashangprpr.app.world.TileMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    private enum class State { TITLE, PLAYING, GAME_OVER, VICTORY }
    private enum class Modal { NONE, SHOP, CRAFT, PAUSE, DIALOGUE, BAG, VILLAGER, TRAVEL, FURNACE }
    private enum class Target {
        NONE, SHOP, CRAFT, GATE, CHEST, MOUND, PORTAL, COOK, SLEEP,
        FURNACE, CAMPFIRE, CAVE_ENTER, CAVE_EXIT, CAVE_NEXT, TOTEM, VILLAGER, BOAT_DOCK
    }

    private class Touch(val type: Int, val id: Int, val x: Float, val y: Float)
    private class Hit(val rect: RectF, val id: String)

    // ---------------------------------------------------------------- boot
    private val boot: Boolean = run {
        Assets.init(context)
        SaveStore.init(context)
        Sound.init(context)
        Sound.sfxOn = SaveStore.bool("sfx", true)
        Sound.musicOn = SaveStore.bool("music", true)
        true
    }

    private var thread: Thread? = null
    @Volatile private var running = false
    private val queue = ConcurrentLinkedQueue<Touch>()
    @Volatile private var surfW = 0
    @Volatile private var surfH = 0
    private var curW = 0
    private var curH = 0

    private var state = State.TITLE
    private var modal = Modal.NONE
    private var player = Player(300f, 880f)
    private var era = EraType.ERA_1_MEDIEVAL
    private var world = TileMap(EraType.ERA_1_MEDIEVAL, 1077L)
    private var camera = Camera2D(world.width, world.height)
    private var zoom = 1f
    private var time = 0f
    private var titleT = 0f
    private var endT = 0f
    private var fade = 1f
    private var shake = 0f
    private var hitStop = 0f
    private var deathTimer = 0f
    private var hurtFlash = 0f
    private var toastText = ""
    private var toastTimer = 0f
    private var bannerTimer = 0f
    private var waveTimer = 6f
    private val rnd = java.util.Random(99)

    // world lists
    private val enemies = ArrayList<Enemy>()
    private val projectiles = ArrayList<Projectile>()
    private val explosions = ArrayList<ExplosionFx>()
    private val floats = ArrayList<FloatingText>()
    private val coins = ArrayList<CoinDrop>()
    private val chests = ArrayList<TreasureChest>()
    private val crates = ArrayList<WoodCrate>()
    private val rocks = ArrayList<MiningRock>()
    private val trees = ArrayList<ChoppableTree>()
    private val traps = ArrayList<DungeonTrap>()
    private val props = ArrayList<WorldProp>()
    private val gens = ArrayList<PowerGenerator>()
    private val particles = ParticleSystem()
    private var merchant = MerchantNPC(0f, 0f)
    private var table = CraftTable(0f, 0f)
    private var portal = TimePortal(0f, 0f)
    private var mound: BuriedMound? = null
    private var boss: Enemy? = null
    private var bossDown = false
    private var greeted = false
    private var target = Target.NONE
    private var targetChest: TreasureChest? = null
    private var targetMound: BuriedMound? = null
    private var targetCave: CaveEntrance? = null
    private var targetCamp: CampfireNode? = null
    private var targetTotem: CaveTotem? = null
    private var targetVillager: VillagerNPC? = null
    private val atkRect = RectF()

    // open world + cave transition + Minecraft-style building
    private var ow: OpenWorld? = null
    private var inCave = false
    private var caveMap: TileMap? = null
    private var caveFloor = 1
    private var activeCave: CaveEntrance? = null
    private var surfaceX = 0f
    private var surfaceY = 0f
    private var caveExitSurface = CaveExitNode(0f, 0f, false, 1)
    private var caveExitNext: CaveExitNode? = null

    private val beds = ArrayList<Bed>()
    private val furnaces = ArrayList<FurnaceObj>()
    private val campfires = ArrayList<CampfireNode>()
    private val caveEntrances = ArrayList<CaveEntrance>()
    private val cocoons = ArrayList<CaveCocoon>()
    private val totems = ArrayList<CaveTotem>()
    private val cannons = ArrayList<CannonTurret>()
    private val banners = ArrayList<FortressBanner>()
    private val docks = ArrayList<BoatDock>()
    private val mounds = ArrayList<BuriedMound>()
    private val sheeps = ArrayList<Sheep>()
    private val villagers = ArrayList<VillagerNPC>()
    private val owPortals = ArrayList<TimePortal>()
    private val quests = QuestBoard.createDefaultQuests()

    private var dayTime = 0f
    private var dayNo = 1
    private var nightFlag = false
    private var owSpawnTimer = 5f
    private var owSaveTimer = 0f
    private var owExploreTimer = 0f
    private var owRegrowTimer = 0f
    private var lowQuality = false
    private var nightPaint: Paint? = null
    private val shopBought = HashMap<String, Int>()
    private val dayLength = 600f
    private var hunger = 80f
    private var sinceHurt = 10f
    private var regenTimer = 0f
    private var starveTimer = 0f
    private var hungerWarned = false
    private var eyeTimer = 4f
    private var heartTimer = 0f
    private var stalker: Enemy? = null
    private class Eyes(val x: Float, val y: Float, var life: Float, val seed: Float)
    private val eyes = ArrayList<Eyes>()

    private val eatBtn = ActionButton("icons/apple.png", "أكل", Color.parseColor("#FF8F00")).apply { visible = false }
    private val bagBtn = ActionButton("icons/bag.png", "حقيبة", Color.parseColor("#8D6E63")).apply { visible = true }
    private val buildBtn = ActionButton("icons/block_wood.png", "بناء/كسر", Color.parseColor("#FFA000")).apply { visible = false }
    private val spellSwitchBtn = ActionButton("icons/pot10.png", "تبديل سحر", Color.parseColor("#7E57C2")).apply { visible = true }
    private val hotbarRects = Array(9) { RectF() }
    private var gridSelIdx = 0

    private val biomeNames = arrayOf(
        "مملكة القلعة والقرى", "أودية الكهوف المظلمة", "أطلال مدينة الزومبي",
        "بحر وجزر القراصنة", "القطاع صفر (سايبر)", "صدع الفراغ الكوني"
    )
    private val collider: Collider
        get() = if (inCave && caveMap != null) caveMap!! else (ow ?: world)

    // ui
    private val joystick = VirtualJoystick()
    private val attackBtn = ActionButton("icons/item_1.png", "هجوم", Color.parseColor("#FF7043"))
    private val dashBtn = ActionButton("ui/ico/arr_r.png", "اندفاع", Color.parseColor("#26C6DA"), Color.WHITE)
    private val magicBtn = ActionButton("ui/ico/cross.png", "سحر", Color.parseColor("#AB47BC"), Color.WHITE)
    private val slowBtn = ActionButton("ui/ico/refresh.png", "إبطاء", Color.parseColor("#5C6BC0"), Color.WHITE)
    private val bombBtn = ActionButton("icons/bomb.png", "قنبلة", Color.parseColor("#EF5350"))
    private val potionBtn = ActionButton("icons/pot1.png", "علاج", Color.parseColor("#66BB6A"))
    private val interactBtn = ActionButton("ui/ico/excl.png", "تفاعل", Color.parseColor("#FFCA28"), Color.WHITE)
    private var pauseCx = 0f
    private var pauseCy = 0f
    private var pauseR = 0f
    private var vignette: Paint? = null
    private var darkPaint: Paint? = null
    private val screenPaint = Paint()

    // modal state
    private val hits = ArrayList<Hit>()
    private var shopTab = 0
    private var bagTab = 0
    private var scrollY = 0f
    private var selectedId = ""
    private var listRect = RectF()
    private var maxScroll = 0f
    private var mPointer = -1
    private var mDownX = 0f
    private var mDownY = 0f
    private var mLastY = 0f
    private var mMoved = false
    private var dialogue: List<DialogueLine> = emptyList()
    private var dialogueIdx = 0
    private var dialogueTime = 0f
    private var dialogueDone: (() -> Unit)? = null
    private val tmpR = RectF()

    init {
        holder.addCallback(this)
        isFocusable = true
        world.build(Pt(300f, 880f), emptyList())
        lowQuality = SaveStore.bool("lowq", false)
        particles.maxCount = if (lowQuality) 90 else 380
        Sound.playMusic(1)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applySizing()
    }

    private fun applySizing() {
        if (width <= 0 || height <= 0) return
        if (lowQuality) holder.setFixedSize((width * 0.62f).toInt(), (height * 0.62f).toInt())
        else holder.setSizeFromLayout()
    }

    private fun toggleQuality() {
        lowQuality = !lowQuality
        SaveStore.putAll(mapOf("lowq" to lowQuality), false)
        particles.maxCount = if (lowQuality) 90 else 380
        post { applySizing() }
    }

    // ---------------------------------------------------------------- lifecycle
    override fun surfaceCreated(h: SurfaceHolder) {
        startLoop()
    }

    override fun surfaceChanged(h: SurfaceHolder, format: Int, w: Int, hh: Int) {
        surfW = w
        surfH = hh
    }

    override fun surfaceDestroyed(h: SurfaceHolder) {
        stopLoop()
    }

    private fun startLoop() {
        if (thread != null) return
        running = true
        val t = Thread(this)
        thread = t
        t.start()
    }

    private fun stopLoop() {
        running = false
        try {
            thread?.join(700)
        } catch (e: InterruptedException) {
        }
        thread = null
    }

    fun pause() {
        stopLoop()
        if (state == State.PLAYING) {
            saveProgress()
            if (modal == Modal.NONE) modal = Modal.PAUSE
        }
        Sound.pauseAll()
    }

    fun resume() {
        Sound.resumeAll()
        if (holder.surface != null && holder.surface.isValid) startLoop()
    }

    override fun run() {
        var last = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            var dt = (now - last) / 1_000_000_000f
            last = now
            if (dt > 0.05f) dt = 0.05f
            if (surfW > 0 && surfH > 0 && (surfW != curW || surfH != curH)) {
                curW = surfW
                curH = surfH
                layoutUi()
            }
            if (curW > 0) {
                try {
                    processInput()
                    update(dt)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                var c: Canvas? = null
                try {
                    c = holder.lockCanvas()
                    if (c != null) render(c)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    if (c != null) {
                        try {
                            holder.unlockCanvasAndPost(c)
                        } catch (e: Exception) {
                        }
                    }
                }
            }
            val spent = (System.nanoTime() - now) / 1_000_000L
            val frameMs = if (lowQuality) 33L else 16L
            if (spent < frameMs) {
                try {
                    Thread.sleep(frameMs - spent)
                } catch (e: InterruptedException) {
                }
            }
        }
    }

    // ---------------------------------------------------------------- layout
    private fun layoutUi() {
        val w = curW.toFloat()
        val h = curH.toFloat()
        Ui.init(w, h)
        val u = Ui.u
        zoom = h / 720f
        camera.viewW = w / zoom
        camera.viewH = h / zoom
        fun place(b: ActionButton, dx: Float, dy: Float, r: Float) {
            b.cx = w - dx * u
            b.cy = h - dy * u
            b.r = r * u
        }
        place(attackBtn, 115f, 120f, 58f)
        place(buildBtn, 240f, 195f, 42f)
        place(dashBtn, 245f, 80f, 36f)
        place(magicBtn, 95f, 245f, 40f)
        place(spellSwitchBtn, 95f, 340f, 30f)
        place(slowBtn, 225f, 295f, 34f)
        place(bombBtn, 345f, 120f, 33f)
        place(potionBtn, 345f, 215f, 33f)
        place(interactBtn, 175f, 395f, 42f)
        place(eatBtn, 345f, 310f, 33f)
        place(bagBtn, 435f, 120f, 36f)

        // Layout 9-slot Minecraft Hotbar at bottom-center
        val slotS = 48f * u
        val gap = 4f * u
        val totalW = 9 * slotS + 8 * gap
        val startX = w / 2f - totalW / 2f - 40f * u
        val topY = h - slotS - 12f * u
        for (i in 0 until 9) {
            val sx = startX + i * (slotS + gap)
            hotbarRects[i].set(sx, topY, sx + slotS, topY + slotS)
        }

        joystick.layout(w, h, u)
        pauseCx = w - 38f * u
        pauseCy = 38f * u
        pauseR = 26f * u
        val diag = sqrt(w * w + h * h) / 2f
        val vg = Paint()
        vg.shader = RadialGradient(
            w / 2f, h / 2f, diag,
            intArrayOf(0x00000000, 0x00000000, Color.argb(255, 200, 0, 0)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        vignette = vg
        val np = Paint()
        np.shader = RadialGradient(
            0f, 0f, 520f * u,
            intArrayOf(0x00000000, 0x00000000, Color.argb(190, 6, 8, 30), Color.argb(255, 6, 8, 30)),
            floatArrayOf(0f, 0.28f, 0.7f, 1f), Shader.TileMode.CLAMP
        )
        nightPaint = np
        buildDark()
        camera.snapTo(player.cx, player.cy)
    }

    private fun buildDark() {
        val d = if (inCave) 0.85f else era.darkness
        if (d <= 0f || curW <= 0) {
            darkPaint = null
            return
        }
        val r = 470f * Ui.u
        val a = (d * 255f).toInt().coerceIn(0, 255)
        val dp = Paint()
        dp.shader = RadialGradient(
            0f, 0f, r,
            intArrayOf(0x00000000, 0x00000000, Color.argb((a * 0.7f).toInt(), 4, 4, 14), Color.argb(a, 4, 4, 14)),
            floatArrayOf(0f, 0.30f, 0.75f, 1f), Shader.TileMode.CLAMP
        )
        darkPaint = dp
    }

    // ---------------------------------------------------------------- input
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val sx = if (width > 0 && surfW > 0) surfW.toFloat() / width else 1f
        val sy = if (height > 0 && surfH > 0) surfH.toFloat() / height else 1f
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                queue.add(Touch(0, e.getPointerId(i), e.getX(i) * sx, e.getY(i) * sy))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) queue.add(Touch(1, e.getPointerId(i), e.getX(i) * sx, e.getY(i) * sy))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = e.actionIndex
                queue.add(Touch(2, e.getPointerId(i), e.getX(i) * sx, e.getY(i) * sy))
            }
            MotionEvent.ACTION_CANCEL -> queue.add(Touch(3, -1, 0f, 0f))
        }
        return true
    }

    private fun processInput() {
        while (true) {
            val t = queue.poll() ?: break
            handleTouch(t)
        }
    }

    private fun handleTouch(t: Touch) {
        when (state) {
            State.TITLE -> if (t.type == 2) tapTitle(t.x, t.y)
            State.GAME_OVER, State.VICTORY -> if (t.type == 2) tapEnd(t.x, t.y)
            State.PLAYING -> {
                if (modal != Modal.NONE) {
                    modalTouch(t)
                    return
                }
                when (t.type) {
                    0 -> touchDown(t)
                    1 -> joystick.onMove(t.id, t.x, t.y)
                    2 -> releaseAll(t.id)
                    3 -> cancelAll()
                }
            }
        }
    }

    private fun releaseAll(id: Int) {
        joystick.onUp(id)
        attackBtn.onUp(id)
        buildBtn.onUp(id)
        dashBtn.onUp(id)
        magicBtn.onUp(id)
        spellSwitchBtn.onUp(id)
        slowBtn.onUp(id)
        bombBtn.onUp(id)
        potionBtn.onUp(id)
        interactBtn.onUp(id)
        eatBtn.onUp(id)
        bagBtn.onUp(id)
    }

    private fun cancelAll() {
        joystick.cancel()
        attackBtn.cancel()
        buildBtn.cancel()
        dashBtn.cancel()
        magicBtn.cancel()
        spellSwitchBtn.cancel()
        slowBtn.cancel()
        bombBtn.cancel()
        potionBtn.cancel()
        interactBtn.cancel()
        eatBtn.cancel()
        bagBtn.cancel()
        mPointer = -1
    }

    private fun touchDown(t: Touch) {
        val dx = t.x - pauseCx
        val dy = t.y - pauseCy
        if (sqrt(dx * dx + dy * dy) <= pauseR * 1.3f) {
            Sound.play("click")
            cancelAll()
            modal = Modal.PAUSE
            return
        }
        // Check Minecraft Hotbar slots first (when in Open World)
        if (ow != null && !inCave) {
            for (i in 0 until 9) {
                if (hotbarRects[i].contains(t.x, t.y)) {
                    player.hotbarIdx = i
                    Sound.play("click", 0.6f)
                    toast("الشريط السريع: ${hotbarSlotTitle(i)}")
                    return
                }
            }
        }
        if (attackBtn.onDown(t.id, t.x, t.y)) return
        if (buildBtn.onDown(t.id, t.x, t.y)) {
            useHotbarActionAtAim()
            return
        }
        if (dashBtn.onDown(t.id, t.x, t.y)) {
            if (player.dash()) {
                Sound.play("dash")
                particles.burst(player.cx, player.cy, 10, Color.parseColor("#26C6DA"), 160f, 5f, 0.4f)
            } else Sound.play("deny", 0.4f)
            return
        }
        if (magicBtn.onDown(t.id, t.x, t.y)) {
            if (player.castSpell(projectiles)) {
                Sound.play("magic")
                particles.burst(player.cx, player.cy, 10, player.currentSpell.color, 150f, 5f, 0.45f)
            } else Sound.play("deny", 0.5f)
            return
        }
        if (spellSwitchBtn.onDown(t.id, t.x, t.y)) {
            player.nextSpell()
            Sound.play("click")
            toast("القدرة السحرية: ${player.currentSpell.nameAr}")
            return
        }
        if (slowBtn.onDown(t.id, t.x, t.y)) {
            if (player.slowmo()) {
                Sound.play("slowmo")
                toast("الزمن يتباطأ!")
            } else Sound.play("deny", 0.5f)
            return
        }
        if (bombBtn.onDown(t.id, t.x, t.y)) {
            val b = player.throwBomb()
            if (b != null) {
                projectiles.add(b)
                Sound.play("swing")
            } else Sound.play("deny", 0.5f)
            return
        }
        if (potionBtn.onDown(t.id, t.x, t.y)) {
            if (player.usePotion()) {
                Sound.play("potion")
                addFloat("+85", player.cx, player.y, Color.parseColor("#69F0AE"), 30f)
                particles.burst(player.cx, player.cy, 14, Color.parseColor("#69F0AE"), 120f, 6f, 0.7f)
            } else Sound.play("deny", 0.5f)
            return
        }
        if (eatBtn.onDown(t.id, t.x, t.y)) {
            eatBest()
            return
        }
        if (bagBtn.onDown(t.id, t.x, t.y)) {
            openBag()
            return
        }
        if (interactBtn.onDown(t.id, t.x, t.y)) {
            doInteract()
            return
        }
        // If player taps directly in the middle of the world while holding a building block or pickaxe, place/break at tapped tile!
        if (ow != null && !inCave && player.hotbarIdx >= 1 && t.x > curW * 0.28f && t.x < curW * 0.72f && t.y > curH * 0.16f && t.y < curH * 0.80f) {
            val wx = camera.x + t.x / zoom
            val wy = camera.y + t.y / zoom
            if (dist(wx, wy, player.cx, player.cy) <= 270f) {
                val tx = floor(wx / OpenWorld.TS).toInt()
                val ty = floor(wy / OpenWorld.TS).toInt()
                useHotbarActionAtTile(tx, ty)
                return
            }
        }
        joystick.onDown(t.id, t.x, t.y)
    }

    // ---------------------------------------------------------------- Minecraft Hotbar & Block Building Helpers
    private fun hotbarSlotTitle(idx: Int): String = when (idx) {
        0 -> player.weapon.nameAr
        1 -> "معول التكسير والتعدين"
        2 -> "بلوك ألواح خشبية (${player.blkWood})"
        3 -> "بلوك طوب حجري (${player.blkStone})"
        4 -> "جسر وأرضية خشبية (${player.blkBridge})"
        5 -> "باب منزل خشبي (${player.blkDoor})"
        6 -> "شعلة إضاءة (${player.blkTorch})"
        7 -> "سياج دفاعي (${player.blkFence})"
        8 -> "بلوك متفجرات TNT (${player.blkTnt})"
        else -> ""
    }

    private fun hotbarSlotIcon(idx: Int): String = when (idx) {
        0 -> player.weapon.iconAsset
        1 -> "ui/ico/axe.png"
        2 -> "icons/block_wood.png"
        3 -> "icons/block_stone_brick.png"
        4 -> "icons/block_bridge.png"
        5 -> "tiles/door.png"
        6 -> "environment/torch.png"
        7 -> "icons/block_fence.png"
        8 -> "icons/block_tnt.png"
        else -> "icons/item_1.png"
    }

    private fun hotbarSlotCount(idx: Int): Int = when (idx) {
        2 -> player.blkWood
        3 -> player.blkStone
        4 -> player.blkBridge
        5 -> player.blkDoor
        6 -> player.blkTorch
        7 -> player.blkFence
        8 -> player.blkTnt
        else -> -1
    }

    private fun aimTileCoords(): Pair<Int, Int> {
        val tx = floor((player.cx + player.aimX * 68f) / OpenWorld.TS).toInt().coerceIn(2, OpenWorld.N - 3)
        val ty = floor((player.cy + player.aimY * 68f) / OpenWorld.TS).toInt().coerceIn(2, OpenWorld.N - 3)
        return Pair(tx, ty)
    }

    private fun useHotbarActionAtAim() {
        val (tx, ty) = aimTileCoords()
        useHotbarActionAtTile(tx, ty)
    }

    private fun useHotbarActionAtTile(tx: Int, ty: Int) {
        val w = ow ?: return
        if (inCave) return
        val px = tx * OpenWorld.TS + 32f
        val py = ty * OpenWorld.TS + 32f
        val existingCode = w.blockCodeAtTile(tx, ty)

        // Tapping a door toggles it open/closed unless holding Pickaxe
        if ((existingCode == BlockType.DOOR_CLOSED.code || existingCode == 5) && player.hotbarIdx != 1) {
            if (w.toggleDoorAt(tx, ty)) {
                Sound.play("gate", 0.7f)
                return
            }
        }

        // Slot 1 = Pickaxe / Block breaker
        if (player.hotbarIdx == 1) {
            if (existingCode != 0) {
                val removed = w.removeBlockAt(tx, ty)
                if (removed != null) {
                    if (removed == BlockType.TNT_BLOCK) {
                        explode(px, py)
                    } else {
                        player.addBlockCount(removed, 1)
                        Sound.play("chop", 0.8f)
                        particles.burst(px, py, 8, Color.parseColor("#FFE082"), 150f, 5f, 0.4f)
                        addFloat("+1 ${removed.nameAr}", px, py - 18f, Color.parseColor("#FFE082"), 20f)
                    }
                    return
                }
            }
            val i = ty * OpenWorld.N + tx
            if (w.breakable(i)) {
                hitOpenTile(w, i)
                return
            }
            toast("وجّه المعول نحو بلوك أو صخرة أو شجرة لتكسيرها")
            return
        }

        // Slots 2..8 = Place selected Minecraft block!
        val blk = player.selectedBlockType()
        if (blk != null) {
            // If a block is already there, break it back to inventory!
            if (existingCode != 0) {
                val removed = w.removeBlockAt(tx, ty)
                if (removed != null) {
                    if (removed == BlockType.TNT_BLOCK) {
                        explode(px, py)
                    } else {
                        player.addBlockCount(removed, 1)
                        Sound.play("chop", 0.8f)
                        particles.burst(px, py, 6, Color.LTGRAY, 130f, 4f, 0.35f)
                    }
                    return
                }
            }
            val have = player.blockCountFor(blk)
            if (have <= 0) {
                // Auto-craft from raw wood/stone if available!
                if (blk == BlockType.WOOD_WALL && player.wood >= 1) {
                    player.wood -= 1
                    player.blkWood += 3
                } else if (blk == BlockType.STONE_BRICK && player.stone >= 1) {
                    player.stone -= 1
                    player.blkStone += 3
                } else if (blk == BlockType.WOOD_BRIDGE && player.wood >= 1) {
                    player.wood -= 1
                    player.blkBridge += 3
                } else {
                    toast("نفد ${blk.nameAr}! اصنع المزيد من الحقيبة أو طاولة الصناعة")
                    Sound.play("deny", 0.5f)
                    return
                }
            }
            if (w.placeBlock(tx, ty, blk, player.cx, player.cy)) {
                player.addBlockCount(blk, -1)
                Sound.play("chop", 0.85f)
                particles.burst(px, py, 7, Color.parseColor("#FFD54F"), 120f, 4.5f, 0.35f)
                advanceQuest("q_build", 1)
            } else {
                Sound.play("deny", 0.4f)
            }
        }
    }

    private fun toast(s: String) {
        toastText = s
        toastTimer = 2.6f
    }

    private fun addFloat(s: String, x: Float, y: Float, color: Int, size: Float = 26f) {
        floats.add(FloatingText(s, x, y, color, size))
    }

    private fun advanceQuest(id: String, amount: Int = 1) {
        val q = quests.firstOrNull { it.id == id } ?: return
        if (q.claimed) return
        val wasDone = q.isComplete
        q.progress = min(q.targetCount, q.progress + amount)
        if (!wasDone && q.isComplete) {
            Sound.play("era_clear", 0.8f)
            toast("اكتملت مهمة: ${q.titleAr}! افتح الحقيبة لاستلام المكافأة")
        }
    }

    // ---------------------------------------------------------------- game flow
    private fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx
        val dy = ay - by
        return sqrt(dx * dx + dy * dy)
    }

    private fun bossName(e: EraType): String = when (e) {
        EraType.ERA_1_MEDIEVAL -> "الفارس الأسود"
        EraType.ERA_2_CAVES -> "الحارس الحجري"
        EraType.ERA_3_ZOMBIE -> "الجرثومة الأم"
        EraType.ERA_4_ISLAND -> "قبطان القراصنة"
        EraType.ERA_5_CYBER -> "قائد النظام"
        EraType.ERA_6_VOID -> "سيد الفراغ"
    }

    private fun minionRole(e: EraType): EnemyRole = when (e) {
        EraType.ERA_1_MEDIEVAL -> EnemyRole.MELEE
        EraType.ERA_2_CAVES -> EnemyRole.GHOUL
        EraType.ERA_3_ZOMBIE -> EnemyRole.GHOUL
        EraType.ERA_4_ISLAND -> EnemyRole.TRIBAL
        EraType.ERA_5_CYBER -> EnemyRole.COMMANDO
        EraType.ERA_6_VOID -> EnemyRole.CLONE
    }

    private fun backToTitle() {
        ow = null
        inCave = false
        caveMap = null
        player = Player(300f, 880f)
        era = EraType.ERA_1_MEDIEVAL
        world = TileMap(era, 1077L)
        world.build(Pt(300f, 880f), emptyList())
        camera = Camera2D(world.width, world.height)
        camera.viewW = curW / zoom
        camera.viewH = curH / zoom
        enemies.clear()
        projectiles.clear()
        cancelAll()
        modal = Modal.NONE
        state = State.TITLE
        buildDark()
        Sound.playMusic(1)
    }

    private fun startNewGame() {
        SaveStore.clear()
        player = Player(300f, 880f)
        state = State.PLAYING
        startEra(EraType.ERA_1_MEDIEVAL)
    }

    private fun continueGame() {
        val p = Player(300f, 880f)
        val idx = p.load()
        player = p
        state = State.PLAYING
        if (SaveStore.str("mode", "") == "open") {
            startOpen(true)
            return
        }
        startEra(EraType.values()[idx.coerceIn(0, EraType.values().size - 1)])
    }

    private fun retryFromCheckpoint() {
        val idx = player.load()
        state = State.PLAYING
        startEra(EraType.values()[idx.coerceIn(0, EraType.values().size - 1)])
    }

    private fun addEnemy(role: EnemyRole, x: Float, y: Float, key: Boolean = false): Enemy {
        val e = Enemy(x, y, role, era, key)
        enemies.add(e)
        return e
    }

    private fun startEra(e: EraType) {
        era = e
        inCave = false
        caveMap = null
        world = TileMap(e, 1000L + e.ordinal * 77L)
        enemies.clear()
        projectiles.clear()
        explosions.clear()
        floats.clear()
        coins.clear()
        chests.clear()
        crates.clear()
        rocks.clear()
        trees.clear()
        traps.clear()
        props.clear()
        gens.clear()
        cocoons.clear()
        totems.clear()
        cannons.clear()
        particles.list.clear()
        mound = null
        boss = null
        bossDown = false
        greeted = false
        waveTimer = 8f
        ow = null
        shopBought.clear()
        cancelAll()
        val pts = ArrayList<Pt>()
        fun reg(x: Float, y: Float, ox: Float = 32f, oy: Float = 40f) {
            pts.add(Pt(x + ox, y + oy))
        }
        val sx = 300f
        val sy = 880f

        merchant = MerchantNPC(470f, 760f)
        reg(470f, 760f, 48f, 70f)
        world.setDynamic(518f, 840f, true)
        table = CraftTable(620f, 970f)
        reg(620f, 970f)
        world.setDynamic(652f, 1002f, true)
        props.add(WorldProp("environment/fire.png", 392f, 808f, 56f, 56f))
        props.add(WorldProp("environment/torch.png", 350f, 730f, 36f, 36f, 0.15f))
        props.add(WorldProp("environment/torch.png", 580f, 720f, 36f, 36f, 0.15f))

        chests.add(TreasureChest(250f, 440f, 25))
        chests.add(TreasureChest(1500f, 1560f, 35))
        chests.add(TreasureChest(1560f, 520f, 50))
        for (c in chests) {
            reg(c.x, c.y)
            world.setDynamic(c.x + 32f, c.y + 32f, true)
        }
        val crateSpots = listOf(Pt(860f, 470f), Pt(1290f, 340f), Pt(1300f, 1100f), Pt(560f, 1250f))
        for ((i, s) in crateSpots.withIndex()) {
            crates.add(WoodCrate(s.x, s.y, i % 2 == 1))
            reg(s.x, s.y)
            world.setDynamic(s.x + 32f, s.y + 32f, true)
        }
        val rockSpots = listOf(Triple(950f, 320f, false), Triple(1180f, 1560f, true), Triple(700f, 1560f, false), Triple(1480f, 300f, true))
        for (r in rockSpots) {
            rocks.add(MiningRock(r.first, r.second, r.third))
            reg(r.first, r.second)
            world.setDynamic(r.first + 32f, r.second + 32f, true)
        }
        val treeSpots = listOf(Pt(520f, 1380f), Pt(820f, 1480f), Pt(1220f, 1280f), Pt(1560f, 1400f), Pt(430f, 520f))
        for ((i, s) in treeSpots.withIndex()) {
            trees.add(ChoppableTree(s.x, s.y, i % 2 == 0))
            reg(s.x, s.y)
            world.setDynamic(s.x + 32f, s.y + 44f, true)
        }

        if (e.ordinal >= 1) {
            traps.add(DungeonTrap(1000f, 780f, true))
            traps.add(DungeonTrap(1280f, 1180f, true))
            traps.add(DungeonTrap(1130f, 1000f, false))
            traps.add(DungeonTrap(1380f, 600f, false))
            traps.add(DungeonTrap(1450f, 1340f, false))
            for (t in traps) reg(t.baseX, t.baseY, 32f, 32f)
        }

        when (e) {
            EraType.ERA_1_MEDIEVAL -> {
                addEnemy(EnemyRole.SPEARMAN, 1000f, 500f)
                addEnemy(EnemyRole.SPEARMAN, 1250f, 1300f)
                addEnemy(EnemyRole.ARCHER, 1400f, 720f)
                addEnemy(EnemyRole.ARCHER, 900f, 1450f)
                addEnemy(EnemyRole.MELEE, 1560f, 900f)
                addEnemy(EnemyRole.HEAVY, 1450f, 1080f, true)
            }
            EraType.ERA_2_CAVES -> {
                addEnemy(EnemyRole.GHOUL, 1000f, 500f)
                addEnemy(EnemyRole.GHOUL, 1100f, 1400f)
                addEnemy(EnemyRole.SPEARMAN, 1300f, 800f)
                addEnemy(EnemyRole.ARCHER, 1450f, 420f)
                addEnemy(EnemyRole.HEAVY, 1500f, 1120f, true)
            }
            EraType.ERA_3_ZOMBIE -> {
                addEnemy(EnemyRole.MELEE, 1000f, 520f)
                addEnemy(EnemyRole.MELEE, 1200f, 1350f)
                addEnemy(EnemyRole.GHOUL, 1350f, 760f)
                addEnemy(EnemyRole.GHOUL, 900f, 1450f)
                addEnemy(EnemyRole.GHOUL, 1560f, 1000f)
                addEnemy(EnemyRole.HEAVY, 1480f, 1180f, true)
            }
            EraType.ERA_4_ISLAND -> {
                addEnemy(EnemyRole.TRIBAL, 1000f, 560f)
                addEnemy(EnemyRole.TRIBAL, 1250f, 1250f)
                addEnemy(EnemyRole.TRIBAL, 1500f, 800f)
                addEnemy(EnemyRole.ARCHER, 1350f, 480f)
                addEnemy(EnemyRole.ARCHER, 900f, 1420f)
                addEnemy(EnemyRole.SPEARMAN, 1550f, 1150f)
                val m = BuriedMound(1500f, 1300f)
                mound = m
                reg(m.x, m.y)
            }
            EraType.ERA_5_CYBER -> {
                addEnemy(EnemyRole.COMMANDO, 1000f, 520f)
                addEnemy(EnemyRole.COMMANDO, 1250f, 1330f)
                addEnemy(EnemyRole.COMMANDO, 1450f, 760f)
                addEnemy(EnemyRole.COMMANDO, 900f, 1450f)
                addEnemy(EnemyRole.HEAVY, 1500f, 1100f, true)
                gens.add(PowerGenerator(700f, 520f, 1))
                gens.add(PowerGenerator(1300f, 1480f, 2))
                gens.add(PowerGenerator(1500f, 420f, 3))
                for (g in gens) {
                    reg(g.x, g.y, 42f, 42f)
                    world.setDynamic(g.x + 42f, g.y + 42f, true)
                }
            }
            EraType.ERA_6_VOID -> {
                addEnemy(EnemyRole.CLONE, 1000f, 620f)
                addEnemy(EnemyRole.CLONE, 1250f, 1300f)
                addEnemy(EnemyRole.COMMANDO, 1400f, 820f)
                addEnemy(EnemyRole.COMMANDO, 1000f, 1400f)
            }
        }
        for (en in enemies) reg(en.x, en.y, 40f, 60f)

        val b = addEnemy(EnemyRole.BOSS, 2060f, 880f)
        b.shielded = (e == EraType.ERA_5_CYBER)
        boss = b
        reg(b.x, b.y, b.size / 2f, b.size * 0.7f)
        val right1 = addEnemy(minionRole(e), 2000f, 520f)
        val right2 = addEnemy(minionRole(e), 2000f, 1330f)
        reg(right1.x, right1.y, 40f, 60f)
        reg(right2.x, right2.y, 40f, 60f)
        val rc = TreasureChest(2250f, 420f, 60)
        chests.add(rc)
        reg(rc.x, rc.y)
        world.setDynamic(rc.x + 32f, rc.y + 32f, true)
        if (e == EraType.ERA_4_ISLAND) {
            val gem = TreasureChest(2250f, 1420f, 0, true)
            chests.add(gem)
            reg(gem.x, gem.y)
            world.setDynamic(gem.x + 32f, gem.y + 32f, true)
        }
        portal = TimePortal(2230f, 1120f)
        reg(portal.x, portal.y, 70f, 70f)

        world.build(Pt(sx + 48f, sy + 60f), pts)

        player.x = sx
        player.y = sy
        player.resetState()
        camera = Camera2D(world.width, world.height)
        camera.viewW = curW / zoom
        camera.viewH = curH / zoom
        camera.snapTo(player.cx, player.cy)
        buildDark()
        fade = 1f
        bannerTimer = 4.2f
        hitStop = 0f
        deathTimer = 0f
        Sound.playMusic(e.track)
        player.save(e.ordinal)
        openDialogue(Story.intro(e)) { }
    }

    private fun completeEra() {
        Sound.play("portal")
        player.save(min(era.ordinal + 1, EraType.values().size - 1))
        val n = era.next()
        if (n == null) {
            SaveStore.putAll(mapOf("ow_unlocked" to true), false)
            state = State.VICTORY
            endT = 0f
            cancelAll()
            Sound.play("victory")
        } else {
            startEra(n)
        }
    }

    // ---------------------------------------------------------------- dialogue
    private fun openDialogue(lines: List<DialogueLine>, done: () -> Unit) {
        if (lines.isEmpty()) {
            done()
            return
        }
        dialogue = lines
        dialogueIdx = 0
        dialogueTime = 0f
        dialogueDone = done
        modal = Modal.DIALOGUE
        cancelAll()
    }

    private fun dialogueChars(): Int = (dialogueTime * 42f).toInt()

    private fun advanceDialogue() {
        if (dialogueIdx >= dialogue.size) {
            modal = Modal.NONE
            return
        }
        val full = dialogue[dialogueIdx].text.length
        if (dialogueChars() < full) {
            dialogueTime = full / 42f + 0.05f
            return
        }
        Sound.play("click", 0.5f)
        dialogueIdx++
        dialogueTime = 0f
        if (dialogueIdx >= dialogue.size) {
            modal = Modal.NONE
            val d = dialogueDone
            dialogueDone = null
            d?.invoke()
        }
    }

    // ---------------------------------------------------------------- update
    private fun update(dt0: Float) {
        time += dt0
        if (fade > 0f) fade = max(0f, fade - dt0 * 1.1f)
        if (toastTimer > 0f) toastTimer -= dt0
        if (hurtFlash > 0f) hurtFlash -= dt0
        if (shake > 0f) shake = max(0f, shake - dt0 * 30f)
        when (state) {
            State.TITLE -> {
                titleT += dt0
                return
            }
            State.GAME_OVER, State.VICTORY -> {
                endT += dt0
                return
            }
            State.PLAYING -> {
            }
        }
        if (bannerTimer > 0f) bannerTimer -= dt0
        if (modal == Modal.DIALOGUE) dialogueTime += dt0
        if (modal != Modal.NONE) return
        if (hitStop > 0f) {
            hitStop -= dt0
            return
        }
        if (ow != null) {
            updateOpen(dt0)
            return
        }
        val dt = dt0
        val edt = if (player.slowTimer > 0f) dt * 0.3f else dt
        val hpBefore = player.hp

        if (attackBtn.isPressed && !player.isDead) {
            if (player.attack()) Sound.play("swing", 0.7f)
        }
        player.update(dt, joystick.dx, joystick.dy, world)
        camera.update(player.cx, player.cy, dt)
        if (player.isDead) {
            deathTimer += dt
            if (deathTimer > 1.8f) {
                state = State.GAME_OVER
                endT = 0f
                cancelAll()
                Sound.play("gameover")
                return
            }
        }
        resolveMelee()

        var i = 0
        while (i < enemies.size) {
            val en = enemies[i]
            en.update(edt, player, world, projectiles)
            if (en.wantSummon) {
                en.wantSummon = false
                summonMinions(en)
            }
            i++
        }
        separateEnemies()
        val eit = enemies.iterator()
        while (eit.hasNext()) {
            val en = eit.next()
            if (en.isDead && en.deadTimer > 2.5f) eit.remove()
        }

        var pi = projectiles.size - 1
        while (pi >= 0) {
            val pr = projectiles[pi]
            pr.update(edt)
            if (!pr.dead) updateProjectile(pr)
            if (pr.dead) {
                if (pr.kind == Projectile.BOMB || pr.kind == Projectile.CANNONBALL) explode(pr.x, pr.y)
                projectiles.removeAt(pi)
            }
            pi--
        }
        var xi = explosions.size - 1
        while (xi >= 0) {
            explosions[xi].update(dt)
            if (explosions[xi].done) explosions.removeAt(xi)
            xi--
        }
        particles.update(dt)
        var fi = floats.size - 1
        while (fi >= 0) {
            floats[fi].update(dt)
            if (floats[fi].dead) floats.removeAt(fi)
            fi--
        }
        var ci = coins.size - 1
        while (ci >= 0) {
            val c = coins[ci]
            c.update(dt, player)
            if (c.collected) {
                player.coins += c.value
                addFloat("+${c.value}", player.cx, player.y - 10f, Color.parseColor("#FFD740"), 24f)
                Sound.play("coin", 0.7f)
                particles.burst(c.x, c.y, 6, Color.parseColor("#FFD740"), 110f, 5f, 0.4f)
                coins.removeAt(ci)
            }
            ci--
        }

        merchant.update(dt)
        for (p in props) p.update(dt)
        for (t in traps) t.update(dt, player)
        for (r in rocks) r.update(dt)
        for (c in crates) c.update(dt)
        for (t in trees) t.update(dt)
        for (g in gens) g.update(dt)
        mound?.update(dt)
        portal.update(dt)
        portal.isActive = bossDown && (era != EraType.ERA_4_ISLAND || player.hasOceanGem)

        if (era == EraType.ERA_4_ISLAND && !player.hasOceanGem && !bossDown) {
            waveTimer -= dt
            if (waveTimer <= 0f) {
                waveTimer = 15f
                spawnWave()
            }
        }
        if (era == EraType.ERA_6_VOID && !world.gateOpen) {
            val gateX = TileMap.GATE_COL * TileMap.TS
            var left = 0
            for (en in enemies) if (!en.isDead && en !== boss && en.x < gateX) left++
            if (left == 0) openGate()
        }
        if (player.hp < hpBefore) onPlayerHurt(hpBefore - player.hp)
        updateTarget()
        updateButtons()
    }

    private fun onPlayerHurt(d: Int) {
        Sound.play("hurt")
        shake = max(shake, 9f)
        hurtFlash = 0.4f
        addFloat("-$d", player.cx, player.y - 6f, Color.parseColor("#FF5252"), 28f)
        particles.burst(player.cx, player.cy, 10, Color.RED, 170f, 6f, 0.5f)
    }

    private fun separateEnemies() {
        for (i in 0 until enemies.size) {
            val a = enemies[i]
            if (a.isDead) continue
            for (j in i + 1 until enemies.size) {
                val b = enemies[j]
                if (b.isDead) continue
                val dx = b.cx - a.cx
                val dy = b.cy - a.cy
                val d2 = dx * dx + dy * dy
                val minD = 46f
                if (d2 < minD * minD && d2 > 0.01f) {
                    val d = sqrt(d2)
                    val push = (minD - d) * 0.5f
                    collider.move(a, -dx / d * push, -dy / d * push)
                    collider.move(b, dx / d * push, dy / d * push)
                }
            }
        }
    }

    private fun updateProjectile(pr: Projectile) {
        if (collider.isSolidAt(pr.x, pr.y)) {
            val w = ow
            if (w != null && !inCave) {
                val tx = floor(pr.x / OpenWorld.TS).toInt()
                val ty = floor(pr.y / OpenWorld.TS).toInt()
                if (w.blockCodeAtTile(tx, ty) == BlockType.TNT_BLOCK.code) {
                    w.removeBlockAt(tx, ty)
                    explode(tx * OpenWorld.TS + 32f, ty * OpenWorld.TS + 32f)
                }
            }
            pr.dead = true
            particles.burst(pr.x, pr.y, 5, Color.LTGRAY, 90f, 4f, 0.3f)
            return
        }
        if (pr.friendly) {
            for (en in enemies) {
                if (en.isDead || pr.hitSet.contains(en)) continue
                if (en.hitbox.contains(pr.x, pr.y)) {
                    if (pr.kind == Projectile.BOMB || pr.kind == Projectile.CANNONBALL) {
                        pr.dead = true
                        return
                    }
                    damageEnemy(en, pr.damage, pr.x - pr.vx * 0.05f, pr.y - pr.vy * 0.05f, 0.8f)
                    if (pr.pierce) pr.hitSet.add(en) else {
                        pr.dead = true
                        return
                    }
                }
            }
            for (g in gens) {
                if (!g.isDestroyed && !pr.hitSet.contains(g) && g.hitbox.contains(pr.x, pr.y)) {
                    pr.hitSet.add(g)
                    hitGen(g)
                    if (!pr.pierce) {
                        pr.dead = true
                        return
                    }
                }
            }
            for (cc in cocoons) {
                if (!cc.isDestroyed && !pr.hitSet.contains(cc) && cc.hitbox.contains(pr.x, pr.y)) {
                    pr.hitSet.add(cc)
                    hitCocoon(cc)
                    if (!pr.pierce) {
                        pr.dead = true
                        return
                    }
                }
            }
        } else {
            if (player.hitbox.contains(pr.x, pr.y)) {
                player.takeDamage(pr.damage)
                pr.dead = true
            }
        }
    }

    private fun resolveMelee() {
        if (!player.inHitWindow) return
        player.attackRect(atkRect)
        val base = (player.weapon.damage * (1f + 0.12f * player.weaponLvl)).toInt()
        for (en in enemies) {
            if (en.isDead || player.swingHit.contains(en)) continue
            if (RectF.intersects(atkRect, en.hitbox)) {
                player.swingHit.add(en)
                val crit = rnd.nextFloat() < 0.14f
                damageEnemy(en, if (crit) (base * 1.6f).toInt() else base, player.cx, player.cy, 1f, crit)
            }
        }
        for (r in rocks) if (!r.isDestroyed && !player.swingHit.contains(r) && RectF.intersects(atkRect, r.hitbox)) {
            player.swingHit.add(r)
            hitRock(r)
        }
        for (c in crates) if (!c.isDestroyed && !player.swingHit.contains(c) && RectF.intersects(atkRect, c.hitbox)) {
            player.swingHit.add(c)
            hitCrate(c)
        }
        for (t in trees) if (!t.isDestroyed && !player.swingHit.contains(t) && RectF.intersects(atkRect, t.hitbox)) {
            player.swingHit.add(t)
            hitTree(t)
        }
        for (g in gens) if (!g.isDestroyed && !player.swingHit.contains(g) && RectF.intersects(atkRect, g.hitbox)) {
            player.swingHit.add(g)
            hitGen(g)
        }
        for (cc in cocoons) if (!cc.isDestroyed && !player.swingHit.contains(cc) && RectF.intersects(atkRect, cc.hitbox)) {
            player.swingHit.add(cc)
            hitCocoon(cc)
        }
        for (cn in cannons) if (!cn.friendly && !cn.isDestroyed && !player.swingHit.contains(cn) && RectF.intersects(atkRect, cn.hitbox)) {
            player.swingHit.add(cn)
            hitCannon(cn)
        }
        for (sh in sheeps) if (!sh.isDead && !player.swingHit.contains(sh) && RectF.intersects(atkRect, sh.hitbox)) {
            player.swingHit.add(sh)
            hitSheep(sh)
        }
        val w = ow
        if (w != null && !inCave) meleeTiles(w)
    }

    private fun hitSheep(sh: Sheep) {
        if (!sh.hit(player.cx, player.cy)) return
        Sound.play("hit", 0.7f)
        particles.burst(sh.cx, sh.cy, 8, Color.WHITE, 140f, 5f, 0.45f)
        if (sh.woolReady) {
            sh.woolReady = false
            player.wool += 1
            addFloat("+1 صوف", sh.cx, sh.y - 10f, Color.WHITE, 22f)
            advanceQuest("q_wool", 1)
        }
        if (sh.isDead) {
            player.wool += 1
            player.rawMeat += 2
            addFloat("+1 صوف  +2 لحم", sh.cx, sh.y - 28f, Color.parseColor("#FFAB91"), 22f)
            advanceQuest("q_wool", 1)
        }
    }

    private fun hitCocoon(cc: CaveCocoon) {
        if (!cc.hit()) return
        Sound.play("chop", 0.9f)
        particles.burst(cc.x + 40f, cc.y + 40f, 10, Color.parseColor("#CE93D8"), 160f, 6f, 0.45f)
        if (cc.isDestroyed) {
            val b = cc.biome.coerceIn(0, 5)
            player.mats[b] += 2
            coins.add(CoinDrop(cc.x + 40f, cc.y + 40f, 25))
            addFloat("+2 ${Mats.names[b]}", cc.x + 40f, cc.y - 10f, Color.parseColor("#EA80FC"), 24f)
            advanceQuest("q_cave", 1)
        }
    }

    private fun hitCannon(cn: CannonTurret) {
        if (!cn.hit()) return
        Sound.play("mine", 0.9f)
        particles.burst(cn.x + 38f, cn.y + 38f, 8, Color.parseColor("#FF8A65"), 170f, 5f, 0.4f)
        if (cn.isDestroyed) {
            Sound.play("explode", 0.8f)
            player.iron += 2
            coins.add(CoinDrop(cn.x + 38f, cn.y + 38f, 35))
            addFloat("+2 حديد  +35 ذهب", cn.x + 38f, cn.y - 12f, GOLD, 23f)
            advanceQuest("q_pirate", 1)
        }
    }

    private fun damageEnemy(en: Enemy, dmg: Int, fx: Float, fy: Float, knock: Float, crit: Boolean = false) {
        val dealt = en.takeHit(dmg, fx, fy, knock)
        if (dealt == 0) {
            addFloat("محصّن", en.cx, en.y, Color.CYAN, 22f)
            Sound.play("deny", 0.5f)
            return
        }
        val col = if (crit) Color.parseColor("#FFD740") else Color.WHITE
        addFloat(if (crit) "$dealt!" else "$dealt", en.cx, en.y, col, if (crit) 34f else 26f)
        particles.burst(en.cx, en.cy, if (crit) 14 else 8, col, 220f, 6f, 0.4f)
        Sound.play("hit", 0.8f)
        hitStop = if (crit) 0.07f else 0.035f
        shake = max(shake, if (crit) 6f else 3f)
        if (en.isDead) onEnemyDeath(en)
    }

    private fun onEnemyDeath(en: Enemy) {
        val n = if (en.isBoss) 6 else 1
        for (k in 0 until n) coins.add(CoinDrop(en.cx, en.cy, max(1, en.reward / n)))
        particles.burst(en.cx, en.cy, 18, Color.parseColor("#B0BEC5"), 200f, 7f, 0.7f)
        if (ow != null) {
            advanceQuest("q_zombie", 1)
            if (en.era == EraType.ERA_4_ISLAND || en.role == EnemyRole.TRIBAL) advanceQuest("q_pirate", 1)
        }
        if (en === stalker) {
            stalker = null
            player.mats[5] += 2
            addFloat("+2 ${Mats.names[5]}", en.cx, en.y - 40f, Color.parseColor("#D070FF"), 24f)
        }
        if (ow != null && !en.isBoss && rnd.nextFloat() < 0.45f) {
            player.rawMeat++
            addFloat("+1 لحم نيء", en.cx, en.y - 4f, Color.parseColor("#EF9A9A"), 20f)
        }
        if (ow != null && rnd.nextFloat() < 0.12f) {
            player.treasureMaps++
            addFloat("+1 خريطة كنز!", en.cx, en.y - 36f, Color.parseColor("#80DEEA"), 22f)
        }
        if (en.role == EnemyRole.HEAVY || en.isBoss) {
            val mi = en.era.ordinal
            val mn = if (en.isBoss) 3 + rnd.nextInt(2) else 1
            player.mats[mi] += mn
            addFloat("+$mn ${Mats.names[mi]}", en.cx, en.y - 22f, Color.parseColor("#FFD740"), 22f)
            if (en.isBoss && ow != null) {
                player.goldKeys++
                addFloat("+1 مفتاح ذهبي!", en.cx, en.y - 48f, GOLD, 25f)
            }
        }
        if (en.holdsKey) {
            player.hasBossKey = true
            Sound.play("gate")
            toast("حصلت على مفتاح البوابة! اذهب إليها")
        }
        if (en === boss && ow == null) {
            bossDown = true
            shake = 14f
            Sound.play("era_clear")
            openDialogue(Story.clear(era)) { toast("بوابة الزمن أصبحت مفتوحة") }
        } else if (en === boss && inCave) {
            bossDown = true
            shake = 12f
            Sound.play("era_clear")
            toast("هزمت زعيم الكهف! افتح الصناديق الذهبية أو اخرج للسطح")
            advanceQuest("q_cave", 2)
        }
    }

    private fun summonMinions(b: Enemy) {
        var alive = 0
        for (en in enemies) if (!en.isDead && en !== b) alive++
        if (alive >= 7) return
        for (k in 0 until 2) {
            val sx = b.x + (if (k == 0) -110f else 110f)
            val sy = b.y + 40f
            val m = Enemy(sx, sy, minionRole(era), era)
            if (collider.blocked(m.x + m.fOffX, m.y + m.fOffY, m.x + m.fOffX + m.fW, m.y + m.fOffY + m.fH)) continue
            m.aggro = true
            enemies.add(m)
            particles.burst(m.cx, m.cy, 12, Color.parseColor("#E040FB"), 150f, 6f, 0.6f)
        }
        Sound.play("boss", 0.8f)
        addFloat("استدعاء!", b.cx, b.y - 10f, Color.parseColor("#E040FB"), 28f)
    }

    private fun spawnWave() {
        var alive = 0
        for (en in enemies) if (!en.isDead && en !== boss) alive++
        if (alive >= 8) return
        val gateX = TileMap.GATE_COL * TileMap.TS
        var made = 0
        var tries = 0
        while (made < 2 && tries < 30) {
            tries++
            val a = rnd.nextFloat() * 6.2832f
            val r = 480f + rnd.nextFloat() * 160f
            val m = Enemy(
                player.cx + cos(a) * r - 42f,
                player.cy + sin(a) * r - 60f,
                EnemyRole.TRIBAL, era
            )
            if ((m.x < gateX) != (player.x < gateX)) continue
            if (collider.blocked(m.x + m.fOffX, m.y + m.fOffY, m.x + m.fOffX + m.fW, m.y + m.fOffY + m.fH)) continue
            m.aggro = true
            enemies.add(m)
            made++
        }
        if (made > 0) toast("موجة جديدة من القبائل!")
    }

    private fun explode(x: Float, y: Float) {
        explosions.add(ExplosionFx(x, y, 160f))
        Sound.play("explode")
        shake = max(shake, 13f)
        particles.burst(x, y, 26, Color.parseColor("#FF9800"), 340f, 8f, 0.65f)
        for (en in enemies) {
            if (en.isDead) continue
            if (dist(en.cx, en.cy, x, y) < 160f + en.size * 0.3f) damageEnemy(en, 135, x, y, 1.8f)
        }
        for (r in rocks) if (!r.isDestroyed && dist(r.x + 32f, r.y + 32f, x, y) < 160f) {
            hitRock(r)
            if (!r.isDestroyed) hitRock(r)
        }
        for (c in crates) if (!c.isDestroyed && dist(c.x + 32f, c.y + 32f, x, y) < 160f) {
            hitCrate(c)
            if (!c.isDestroyed) hitCrate(c)
        }
        for (t in trees) if (!t.isDestroyed && dist(t.x + 32f, t.y + 40f, x, y) < 160f) hitTree(t)
        for (g in gens) if (!g.isDestroyed && dist(g.x + 42f, g.y + 42f, x, y) < 165f) hitGen(g)
        for (cc in cocoons) if (!cc.isDestroyed && dist(cc.x + 40f, cc.y + 40f, x, y) < 165f) hitCocoon(cc)
        for (cn in cannons) if (!cn.friendly && !cn.isDestroyed && dist(cn.x + 38f, cn.y + 38f, x, y) < 165f) hitCannon(cn)
        val w = ow
        if (w != null && !inCave) openExplode(w, x, y)
    }

    private fun hitRock(r: MiningRock) {
        if (!r.hit()) return
        Sound.play("mine", 0.8f)
        particles.burst(r.x + 32f, r.y + 32f, 6, Color.parseColor("#B0BEC5"), 150f, 5f, 0.4f)
        val amount = if (r.isDestroyed) 2 else 1
        if (r.isIron) player.iron += amount else player.stone += amount
        addFloat("+$amount ${if (r.isIron) "حديد" else "حجر"}", r.x + 32f, r.y - 6f, if (r.isIron) Color.parseColor("#80D8FF") else Color.parseColor("#CFD8DC"), 22f)
        if (r.isDestroyed) {
            val tm = if (inCave) caveMap else world
            tm?.setDynamic(r.x + 32f, r.y + 32f, false)
            if (r.isIron) {
                val bi = if (inCave) (activeCave?.biome ?: 1) else era.ordinal
                player.mats[bi.coerceIn(0, 5)] += 1
                addFloat("+1 ${Mats.names[bi.coerceIn(0, 5)]}", r.x + 32f, r.y - 28f, Color.parseColor("#FFD740"), 22f)
            }
        }
    }

    private fun hitCrate(c: WoodCrate) {
        if (!c.hit()) return
        Sound.play("chop", 0.8f)
        particles.burst(c.x + 32f, c.y + 32f, 6, Color.parseColor("#A1887F"), 150f, 5f, 0.4f)
        if (c.isDestroyed) {
            val tm = if (inCave) caveMap else world
            tm?.setDynamic(c.x + 32f, c.y + 32f, false)
            player.wood += 1
            addFloat("+1 خشب", c.x + 32f, c.y - 6f, Color.parseColor("#FFCC80"), 22f)
            if (rnd.nextFloat() < 0.5f) coins.add(CoinDrop(c.x + 32f, c.y + 32f, 4 + rnd.nextInt(6)))
        }
    }

    private fun hitTree(t: ChoppableTree) {
        if (!t.hit()) return
        Sound.play("chop", 0.8f)
        particles.burst(t.x + 32f, t.y + 30f, 6, Color.parseColor("#66BB6A"), 140f, 5f, 0.4f)
        val amount = if (t.isDestroyed) 2 else 1
        player.wood += amount
        addFloat("+$amount خشب", t.x + 32f, t.y - 30f, Color.parseColor("#FFCC80"), 22f)
        if (t.isDestroyed) world.setDynamic(t.x + 32f, t.y + 44f, false)
    }

    private fun hitGen(g: PowerGenerator) {
        if (!g.hit()) return
        Sound.play("mine", 0.9f)
        particles.burst(g.x + 42f, g.y + 42f, 10, Color.parseColor("#00E5FF"), 200f, 6f, 0.5f)
        if (g.isDestroyed) {
            world.setDynamic(g.x + 42f, g.y + 42f, false)
            Sound.play("explode", 0.8f)
            var left = 0
            for (o in gens) if (!o.isDestroyed) left++
            if (left == 0) {
                boss?.shielded = false
                toast("انهار درع القائد! هاجمه الآن")
            } else toast("تبقّى $left مولد")
        }
    }

    // ---------------------------------------------------------------- interaction
    private fun openGate() {
        if (world.gateOpen) return
        world.gateOpen = true
        Sound.play("gate")
        shake = 10f
        toast("انفتحت البوابة!")
    }

    private fun updateTarget() {
        var best = Target.NONE
        var bestD = 1e9f
        targetChest = null
        fun cand(t: Target, d: Float, lim: Float) {
            if (d < lim && d < bestD) {
                best = t
                bestD = d
            }
        }
        val px = player.cx
        val py = player.cy
        cand(Target.SHOP, dist(px, py, merchant.x + 48f, merchant.y + 60f), 130f)
        cand(Target.CRAFT, dist(px, py, table.x + 32f, table.y + 32f), 112f)
        if (!world.gateOpen && era != EraType.ERA_6_VOID) cand(Target.GATE, dist(px, py, world.gateCenterX, world.gateCenterY), 150f)
        for (c in chests) {
            if (c.isNear(px, py)) {
                val d = dist(px, py, c.x + 32f, c.y + 32f)
                if (d < bestD) {
                    cand(Target.CHEST, d, 100f)
                    if (best == Target.CHEST) targetChest = c
                }
            }
        }
        val m = mound
        if (m != null && m.isNear(px, py)) cand(Target.MOUND, dist(px, py, m.x + 32f, m.y + 32f), 95f)
        if (portal.isNear(px, py)) cand(Target.PORTAL, dist(px, py, portal.x + 70f, portal.y + 70f), 110f)
        target = best
        interactBtn.visible = best != Target.NONE && !player.isDead
        styleInteractButton(best)
    }

    private fun styleInteractButton(best: Target) {
        when (best) {
            Target.SHOP -> { interactBtn.iconPath = "ui/ico/cart.png"; interactBtn.label = "المتجر"; interactBtn.tintIcon = Color.WHITE }
            Target.CRAFT -> { interactBtn.iconPath = "ui/ico/axe.png"; interactBtn.label = "صناعة"; interactBtn.tintIcon = Color.WHITE }
            Target.GATE -> { interactBtn.iconPath = "icons/key.png"; interactBtn.label = "البوابة"; interactBtn.tintIcon = null }
            Target.CHEST -> { interactBtn.iconPath = "icons/chest_gold.png"; interactBtn.label = "افتح"; interactBtn.tintIcon = null }
            Target.MOUND -> { interactBtn.iconPath = "ui/ico/axe.png"; interactBtn.label = "احفر"; interactBtn.tintIcon = Color.WHITE }
            Target.PORTAL -> { interactBtn.iconPath = "ui/ico/enter.png"; interactBtn.label = "بوابة"; interactBtn.tintIcon = Color.WHITE }
            Target.COOK -> { interactBtn.iconPath = "icons/cooked_meat.png"; interactBtn.label = "طبخ"; interactBtn.tintIcon = null }
            Target.FURNACE -> { interactBtn.iconPath = "icons/furnace.png"; interactBtn.label = "الفرن"; interactBtn.tintIcon = null }
            Target.SLEEP -> { interactBtn.iconPath = "icons/bed.png"; interactBtn.label = "نوم"; interactBtn.tintIcon = null }
            Target.CAMPFIRE -> { interactBtn.iconPath = "environment/bonefire.png"; interactBtn.label = "مخيم/سفر"; interactBtn.tintIcon = null }
            Target.CAVE_ENTER -> { interactBtn.iconPath = "ui/ico/enter.png"; interactBtn.label = "دخول كهف"; interactBtn.tintIcon = Color.WHITE }
            Target.CAVE_EXIT -> { interactBtn.iconPath = "ui/ico/exit.png"; interactBtn.label = "خروج للسطح"; interactBtn.tintIcon = Color.WHITE }
            Target.CAVE_NEXT -> { interactBtn.iconPath = "ui/ico/enter.png"; interactBtn.label = "طابق أعمق"; interactBtn.tintIcon = Color.WHITE }
            Target.TOTEM -> { interactBtn.iconPath = "ui/ico/skull.png"; interactBtn.label = "تحدي الكهف"; interactBtn.tintIcon = Color.WHITE }
            Target.VILLAGER -> { interactBtn.iconPath = "icons/goblet.png"; interactBtn.label = "تجارة/سرقة"; interactBtn.tintIcon = null }
            Target.BOAT_DOCK -> { interactBtn.iconPath = "icons/boat.png"; interactBtn.label = "إبحار"; interactBtn.tintIcon = null }
            Target.NONE -> {}
        }
    }

    private fun doInteract() {
        when (target) {
            Target.SHOP -> {
                Sound.play("click")
                if (!greeted) {
                    greeted = true
                    openDialogue(Story.merchantGreeting()) { openShop() }
                } else openShop()
            }
            Target.CRAFT -> openCraft()
            Target.GATE -> {
                if (player.hasBossKey) openGate() else {
                    toast(Story.keyHint(era))
                    Sound.play("deny")
                }
            }
            Target.CHEST -> {
                val c = targetChest
                if (c != null) openChest(c)
            }
            Target.MOUND -> {
                val m = if (ow != null) targetMound else mound
                if (m != null) {
                    if (!player.hasPickaxe) {
                        toast("تحتاج فأس التنقيب! اصنعه من طاولة الصناعة")
                        Sound.play("deny")
                    } else {
                        m.isDug = true
                        Sound.play("mine")
                        particles.burst(m.x + 32f, m.y + 32f, 18, Color.parseColor("#FFD54F"), 210f, 6f, 0.6f)
                        if (ow != null) {
                            val reward = 65 + rnd.nextInt(55)
                            player.coins += reward
                            player.goldKeys++
                            addFloat("+$reward ذهب  +1 مفتاح ذهبي!", m.x + 32f, m.y - 10f, GOLD, 24f)
                            toast("استخرجت كنزاً مدفوناً ومفتاحاً ذهبياً!")
                            advanceQuest("q_treasure", 1)
                        } else {
                            player.hasBossKey = true
                            toast("وجدت مفتاح البوابة تحت الرمال!")
                        }
                    }
                }
            }
            Target.PORTAL -> {
                if (ow != null) openTravelModal() else completeEra()
            }
            Target.COOK -> cookAll(false)
            Target.FURNACE -> {
                Sound.play("click")
                cancelAll()
                modal = Modal.FURNACE
            }
            Target.SLEEP -> trySleep()
            Target.CAMPFIRE -> {
                val cp = targetCamp
                if (cp != null) {
                    if (!cp.isLit) {
                        if (player.wood >= 2) {
                            player.wood -= 2
                            cp.isLit = true
                            Sound.play("magic")
                            toast("أشعلت ${cp.nameAr}! تم فتح السفر السريع إليه")
                        } else {
                            toast("تحتاج 2 خشب لإشعال المخيم")
                            Sound.play("deny")
                        }
                    } else {
                        openTravelModal()
                    }
                }
            }
            Target.CAVE_ENTER -> {
                val ce = targetCave
                if (ce != null) enterCave(ce, 1)
            }
            Target.CAVE_EXIT -> exitCaveToSurface()
            Target.CAVE_NEXT -> {
                val ce = activeCave
                if (ce != null) enterCave(ce, caveFloor + 1)
            }
            Target.TOTEM -> {
                val tt = targetTotem
                if (tt != null && !tt.activated) {
                    tt.activated = true
                    Sound.play("boss")
                    shake = 12f
                    player.goldKeys++
                    val b = tt.biome.coerceIn(0, 5)
                    player.mats[b] += 2
                    addFloat("+1 مفتاح ذهبي  +2 ${Mats.names[b]}", tt.x + 44f, tt.y - 10f, GOLD, 24f)
                    for (k in 0 until 3) {
                        val ang = k * 2.1f
                        val en = Enemy(tt.x + cos(ang) * 140f, tt.y + sin(ang) * 140f, EnemyRole.HEAVY, EraType.values()[b])
                        en.aggro = true
                        enemies.add(en)
                    }
                    toast("تفعّل مذبح التحدي! اهزم الحراس النخبة")
                    advanceQuest("q_cave", 1)
                }
            }
            Target.VILLAGER -> {
                if (targetVillager != null) {
                    Sound.play("click")
                    cancelAll()
                    modal = Modal.VILLAGER
                }
            }
            Target.BOAT_DOCK -> {
                if (!player.hasBoat) {
                    player.hasBoat = true
                    Sound.play("era_clear", 0.7f)
                    toast("حصلت على مركب القرية! يمكنك الآن الإبحار في المياه العميقة")
                } else {
                    toast("مركبك جاهز! اتجه للمياه للإبحار تلقائياً")
                }
            }
            Target.NONE -> {}
        }
    }

    private fun openChest(c: TreasureChest) {
        if (c.requiresGoldKey && player.goldKeys <= 0) {
            toast("صندوق ذهبي مقفل! تحتاج مفتاحاً ذهبياً (من المتجر أو الصناعة أو الزعماء)")
            Sound.play("deny")
            return
        }
        if (c.requiresGoldKey) {
            player.goldKeys--
            advanceQuest("q_treasure", 1)
        }
        val g = c.open()
        Sound.play("coin")
        particles.burst(c.x + 32f, c.y + 20f, 16, Color.parseColor("#FFD740"), 190f, 6f, 0.6f)
        if (c.isGem) {
            player.hasOceanGem = true
            toast("حصلت على جوهرة المحيط!")
        } else {
            player.coins += g
            addFloat("+$g ذهب", c.x + 32f, c.y - 6f, Color.parseColor("#FFD740"), 30f)
            if (c.requiresGoldKey) {
                val bi = if (inCave) (activeCave?.biome ?: 1) else (ow?.biomeAtPx(c.x, c.y) ?: era.ordinal)
                player.mats[bi.coerceIn(0, 5)] += 2
                player.potions += 1
                addFloat("+2 ${Mats.names[bi.coerceIn(0, 5)]}  +1 جرعة", c.x + 32f, c.y - 34f, Color.parseColor("#69F0AE"), 22f)
            }
            if (inCave) advanceQuest("q_cave", 1)
        }
    }

    private fun updateButtons() {
        attackBtn.iconPath = player.weapon.iconAsset
        attackBtn.accent = player.weapon.color
        dashBtn.cooldown = player.cooldownFrac(player.dashCd, Player.DASH_CD)

        // Update Build/Break button according to selected Minecraft Hotbar slot
        buildBtn.visible = ow != null && !inCave && !player.isDead && player.hotbarIdx >= 1
        if (buildBtn.visible) {
            buildBtn.iconPath = hotbarSlotIcon(player.hotbarIdx)
            val cnt = hotbarSlotCount(player.hotbarIdx)
            buildBtn.badge = if (cnt >= 0) cnt.toString() else null
            buildBtn.label = if (player.hotbarIdx == 1) "كسر ⛏️" else "بناء 🧱"
            buildBtn.accent = if (player.hotbarIdx == 1) Color.parseColor("#FFB74D") else Color.parseColor("#8BC34A")
        }

        val sp = player.currentSpell
        magicBtn.iconPath = sp.iconAsset
        magicBtn.accent = sp.color
        magicBtn.label = sp.nameAr.substringBefore(" ")
        magicBtn.cooldown = player.cooldownFrac(player.magicCd, sp.cooldown)
        magicBtn.enabled = player.mana >= sp.manaCost
        slowBtn.cooldown = if (player.slowTimer > 0f) 0f else player.cooldownFrac(player.slowCd, Player.SLOW_CD)
        slowBtn.enabled = player.mana >= Player.SLOW_COST || player.slowTimer > 0f
        bombBtn.badge = player.bombs.toString()
        bombBtn.enabled = player.bombs > 0
        bombBtn.cooldown = player.cooldownFrac(player.bombCd, Player.BOMB_CD)
        potionBtn.badge = player.potions.toString()
        potionBtn.enabled = player.potions > 0 && player.hp < player.maxHp
        eatBtn.visible = ow != null && !player.isDead
        eatBtn.badge = foodCount().toString()
        eatBtn.enabled = foodCount() > 0 && hunger < 98f
        eatBtn.iconPath = when {
            player.stew > 0 -> "icons/stew.png"
            player.cookedMeat > 0 -> "icons/cooked_meat.png"
            player.apples > 0 -> "icons/apple.png"
            else -> "icons/raw_meat.png"
        }
        val readyQuests = quests.count { it.isComplete && !it.claimed }
        bagBtn.badge = if (readyQuests > 0) "!$readyQuests" else null
    }

    // ---------------------------------------------------------------- modal logic
    private fun openShop() {
        modal = Modal.SHOP
        shopTab = 0
        scrollY = 0f
        selectedId = ""
        mPointer = -1
        cancelAll()
    }

    private fun openCraft() {
        modal = Modal.CRAFT
        scrollY = 0f
        selectedId = ""
        mPointer = -1
        cancelAll()
        Sound.play("click")
    }

    private fun openBag() {
        modal = Modal.BAG
        bagTab = 0
        scrollY = 0f
        selectedId = ""
        mPointer = -1
        cancelAll()
        Sound.play("click")
    }

    private fun openTravelModal() {
        if (inCave) {
            toast("اخرج لسطح العالم المفتوح أولاً للسفر السريع")
            return
        }
        modal = Modal.TRAVEL
        scrollY = 0f
        mPointer = -1
        cancelAll()
        Sound.play("click")
    }

    private fun modalTouch(t: Touch) {
        when (t.type) {
            0 -> if (mPointer == -1) {
                mPointer = t.id
                mDownX = t.x
                mDownY = t.y
                mLastY = t.y
                mMoved = false
            }
            1 -> if (t.id == mPointer) {
                if (!mMoved && abs(t.y - mDownY) > 14f * Ui.u) mMoved = true
                if (mMoved && (modal == Modal.SHOP || modal == Modal.CRAFT || (modal == Modal.BAG && bagTab != 0) || modal == Modal.TRAVEL || modal == Modal.FURNACE || modal == Modal.VILLAGER)) {
                    scrollY = (scrollY - (t.y - mLastY)).coerceIn(0f, maxScroll)
                }
                mLastY = t.y
            }
            2 -> if (t.id == mPointer) {
                mPointer = -1
                if (!mMoved) tapModal(t.x, t.y)
            }
            3 -> mPointer = -1
        }
    }

    private fun hitAt(x: Float, y: Float): String? {
        var i = hits.size - 1
        while (i >= 0) {
            if (hits[i].rect.contains(x, y)) return hits[i].id
            i--
        }
        return null
    }

    private fun tapModal(x: Float, y: Float) {
        when (modal) {
            Modal.DIALOGUE -> advanceDialogue()
            Modal.PAUSE -> tapPause(hitAt(x, y))
            Modal.SHOP -> tapList(hitAt(x, y), x, y, true)
            Modal.CRAFT -> tapList(hitAt(x, y), x, y, false)
            Modal.BAG -> tapBag(hitAt(x, y))
            Modal.VILLAGER -> tapVillagerModal(hitAt(x, y))
            Modal.TRAVEL -> tapTravelModal(hitAt(x, y))
            Modal.FURNACE -> tapFurnaceModal(hitAt(x, y))
            Modal.NONE -> {}
        }
    }

    private fun tapPause(id: String?) {
        when (id) {
            "resume" -> {
                Sound.play("click")
                modal = Modal.NONE
            }
            "bag" -> {
                openBag()
            }
            "sfx" -> {
                Sound.sfxOn = !Sound.sfxOn
                SaveStore.putAll(mapOf("sfx" to Sound.sfxOn), false)
                Sound.play("click")
            }
            "music" -> {
                Sound.setMusic(!Sound.musicOn)
                SaveStore.putAll(mapOf("music" to Sound.musicOn), false)
                Sound.play("click")
            }
            "quality" -> {
                Sound.play("click")
                toggleQuality()
            }
            "title" -> {
                Sound.play("click")
                saveProgress()
                backToTitle()
            }
        }
    }

    private fun tapList(id: String?, x: Float, y: Float, shop: Boolean) {
        if (id == null) return
        if (id == "close") {
            Sound.play("click")
            saveProgress()
            modal = Modal.NONE
            return
        }
        if (id.startsWith("tab:")) {
            shopTab = id.substring(4).toInt()
            scrollY = 0f
            selectedId = ""
            Sound.play("click")
            return
        }
        if (!listRect.contains(x, y)) return
        val isBuy = id.startsWith("buy:")
        val key = id.substring(4)
        if (selectedId != key) {
            selectedId = key
            Sound.play("click")
            return
        }
        if (!isBuy) return
        if (shop) {
            val item = GameCatalogue.shopItems.firstOrNull { it.id == key } ?: return
            buyItem(item)
        } else {
            val r = CraftingRegistry.recipes.firstOrNull { it.id == key } ?: return
            craftItem(r)
        }
    }

    private fun upgCount(id: String): Int = if (id == "up_hp") player.upgHp else player.upgMana

    private fun tier(): Int = if (ow != null) 5 else era.ordinal

    private fun priceOf(item: ShopItem): Int {
        var p = item.price.toFloat() * (1f + 0.15f * tier())
        if (item.type == ItemType.UPGRADE) p *= (1 + upgCount(item.id)).toFloat()
        return p.toInt()
    }

    private fun stockLimit(item: ShopItem): Int = when (item.id) {
        "potion_heal" -> 5
        "bombs" -> 4
        else -> 0
    }

    private fun stockLeft(item: ShopItem): Int {
        val lim = stockLimit(item)
        return if (lim == 0) 99 else lim - (shopBought[item.id] ?: 0)
    }

    private fun shopState(item: ShopItem): Int {
        val done = when (item.type) {
            ItemType.WEAPON, ItemType.ARMOR -> player.owned.contains(item.id)
            ItemType.UPGRADE -> upgCount(item.id) >= item.maxCount
            ItemType.CONSUMABLE -> false
        }
        if (done) return 1
        if (stockLeft(item) <= 0) return 3
        return if (player.coins >= priceOf(item)) 0 else 2
    }

    private fun applyArmor() {
        val old = player.maxHp
        player.armorId = if (player.owned.contains("armor_nano")) "armor_nano"
        else if (player.owned.contains("armor_royal")) "armor_royal" else ""
        player.recomputeStats()
        player.hp = min(player.maxHp, player.hp + max(0, player.maxHp - old))
    }

    private fun buyItem(item: ShopItem) {
        val st = shopState(item)
        if (st == 1) {
            toast("اشتريته بالفعل")
            Sound.play("deny")
            return
        }
        if (st == 3) {
            toast("نفد المخزون اليوم")
            Sound.play("deny")
            return
        }
        if (st == 2) {
            toast("ذهبك لا يكفي")
            Sound.play("deny")
            return
        }
        player.coins -= priceOf(item)
        shopBought[item.id] = (shopBought[item.id] ?: 0) + 1
        when (item.id) {
            "dagger_silver", "sword_fire", "sword_pirate", "spear_chrono" -> {
                player.owned.add(item.id)
                player.equipBest()
            }
            "armor_royal" -> {
                player.owned.add(item.id)
                applyArmor()
            }
            "potion_heal" -> player.potions += 2
            "pot_speed" -> player.speedPots += 1
            "stew_pack" -> player.stew += 2
            "bombs" -> player.bombs += 2
            "key_gold" -> player.goldKeys += 1
            "treasure_map" -> {
                player.treasureMaps += 1
                spawnRandomTreasureMound()
            }
            "up_hp" -> {
                player.upgHp++
                player.recomputeStats()
                player.hp = min(player.maxHp, player.hp + 30)
            }
            "up_mana" -> {
                player.upgMana++
                player.recomputeStats()
            }
        }
        Sound.play("buy")
        toast("تم الشراء: ${item.titleAr}")
        selectedId = ""
        saveProgress()
    }

    private fun costOf(r: CraftRecipe): Cost {
        if (r.resultType == "UPG_WEAPON" || r.resultType == "UPG_ARMOR") {
            val lvl = if (r.resultType == "UPG_WEAPON") player.weaponLvl else player.armorLvl
            val m = IntArray(6)
            m[min(lvl, 4)] = 2 + lvl / 2
            val scale = if (r.resultType == "UPG_WEAPON") 1f else 0.9f
            val gold = ((60 * (lvl + 1) + 20 * lvl * lvl) * scale).toInt()
            return Cost(0, 3 + 2 * lvl, 2 + 2 * lvl, gold, m, 0)
        }
        return Cost(r.wood, r.stone, r.iron, r.gold, r.mats, r.wool)
    }

    private fun craftState(r: CraftRecipe): Int {
        val done = when (r.resultType) {
            "PICKAXE" -> player.hasPickaxe
            "BOAT" -> player.hasBoat
            "PLASMA" -> player.hasPlasma
            "SHIELD" -> player.owned.contains("armor_nano")
            "GODBLADE" -> player.owned.contains("godblade")
            "UPG_WEAPON" -> player.weaponLvl >= CraftingRegistry.MAX_UPG
            "UPG_ARMOR" -> player.armorLvl >= CraftingRegistry.MAX_UPG
            else -> false
        }
        if (done) return 1
        val c = costOf(r)
        var ok = player.wood >= c.wood && player.stone >= c.stone && player.iron >= c.iron &&
            player.coins >= c.gold && player.wool >= c.wool
        for (i in 0 until 6) if (player.mats[i] < c.mats[i]) ok = false
        return if (ok) 0 else 2
    }

    private fun craftItem(r: CraftRecipe) {
        val st = craftState(r)
        if (st == 1) {
            toast("مملوك أو وصلت للحد الأقصى")
            Sound.play("deny")
            return
        }
        if (st == 2) {
            toast("الموارد أو الصوف أو الذهب غير كافية")
            Sound.play("deny")
            return
        }
        val c = costOf(r)
        player.wood -= c.wood
        player.stone -= c.stone
        player.iron -= c.iron
        player.coins -= c.gold
        player.wool -= c.wool
        for (i in 0 until 6) player.mats[i] -= c.mats[i]
        when (r.resultType) {
            "BLK_WOOD" -> player.blkWood += 6
            "BLK_STONE" -> player.blkStone += 6
            "BLK_BRIDGE" -> player.blkBridge += 6
            "BLK_DOOR" -> player.blkDoor += 2
            "BLK_FENCE" -> player.blkFence += 6
            "BLK_TORCH" -> player.blkTorch += 6
            "BLK_TNT" -> player.blkTnt += 2
            "BED" -> player.bedsItem++
            "FURNACE" -> player.furnacesItem++
            "BOAT" -> player.hasBoat = true
            "CANNON" -> player.cannonsItem++
            "CAMPFIRE" -> player.campfiresItem++
            "PICKAXE" -> player.hasPickaxe = true
            "GOLD_KEY" -> player.goldKeys++
            "STEW" -> {
                player.stew += 2
                advanceQuest("q_cook", 2)
            }
            "SPEED_POT" -> player.speedPots += 2
            "BOMB" -> player.bombs += 2
            "PLASMA" -> player.hasPlasma = true
            "SHIELD" -> {
                player.owned.add("armor_nano")
                applyArmor()
            }
            "GODBLADE" -> {
                player.owned.add("godblade")
                player.equipBest()
            }
            "UPG_WEAPON" -> player.weaponLvl++
            "UPG_ARMOR" -> {
                player.armorLvl++
                player.recomputeStats()
                player.hp = min(player.maxHp, player.hp + 25)
            }
        }
        Sound.play("buy")
        toast("تم الصنع: ${r.titleAr}")
        selectedId = ""
        saveProgress()
    }

    private var titleTaps = 0

    private fun tapTitle(x: Float, y: Float) {
        if (hitAt(x, y) == null && y < curH * 0.32f) {
            titleTaps++
            if (titleTaps >= 5) {
                titleTaps = 0
                SaveStore.putAll(mapOf("ow_unlocked" to true), false)
                toast("تم فتح العالم المفتوح للتجربة")
                Sound.play("era_clear")
            }
            return
        }
        when (hitAt(x, y)) {
            "new" -> {
                Sound.play("click")
                startNewGame()
            }
            "cont" -> {
                Sound.play("click")
                continueGame()
            }
            "sfx" -> {
                Sound.sfxOn = !Sound.sfxOn
                SaveStore.putAll(mapOf("sfx" to Sound.sfxOn), false)
                Sound.play("click")
            }
            "music" -> {
                Sound.setMusic(!Sound.musicOn)
                SaveStore.putAll(mapOf("music" to Sound.musicOn), false)
            }
            "quality" -> {
                Sound.play("click")
                toggleQuality()
            }
            "open" -> {
                Sound.play("click")
                val p = Player(300f, 880f)
                p.load()
                player = p
                startOpen(SaveStore.str("mode", "") == "open")
            }
        }
    }

    private fun tapEnd(x: Float, y: Float) {
        if (endT < 0.8f) return
        when (hitAt(x, y)) {
            "retry" -> {
                Sound.play("click")
                retryFromCheckpoint()
            }
            "open" -> {
                Sound.play("click")
                startOpen(false)
            }
            "title" -> {
                Sound.play("click")
                backToTitle()
            }
        }
    }

    // ---------------------------------------------------------------- rendering
    private val GOLD = Color.parseColor("#FFD54F")

    private fun render(c: Canvas) {
        c.drawColor(Color.BLACK)
        hits.clear()
        when (state) {
            State.TITLE -> drawTitle(c)
            State.PLAYING -> {
                if (ow != null) {
                    drawOpenWorld(c)
                    drawOpenHud(c)
                } else {
                    drawWorld(c)
                    drawHud(c)
                }
                when (modal) {
                    Modal.SHOP -> drawList(c, true)
                    Modal.CRAFT -> drawList(c, false)
                    Modal.PAUSE -> drawPause(c)
                    Modal.DIALOGUE -> drawDialogue(c)
                    Modal.BAG -> drawBagModal(c)
                    Modal.VILLAGER -> drawVillagerModal(c)
                    Modal.TRAVEL -> drawTravelModal(c)
                    Modal.FURNACE -> drawFurnaceModal(c)
                    Modal.NONE -> {}
                }
            }
            State.GAME_OVER -> {
                drawWorld(c)
                drawGameOver(c)
            }
            State.VICTORY -> drawVictory(c)
        }
        drawToast(c)
        if (fade > 0f) {
            screenPaint.color = Color.argb((fade * 255f).toInt().coerceIn(0, 255), 0, 0, 0)
            c.drawRect(0f, 0f, curW.toFloat(), curH.toFloat(), screenPaint)
        }
    }

    private fun drawWorld(c: Canvas) {
        val sh = shake
        val ox = if (sh > 0f) (rnd.nextFloat() - 0.5f) * sh * 2f else 0f
        val oy = if (sh > 0f) (rnd.nextFloat() - 0.5f) * sh * 2f else 0f
        c.save()
        c.scale(zoom, zoom)
        c.translate(-camera.x + ox, -camera.y + oy)
        val vx = camera.x - 12f
        val vy = camera.y - 12f
        val vw = camera.viewW + 24f
        val vh = camera.viewH + 24f
        world.draw(c, vx, vy, vw, vh, time)
        world.drawDecor(c, vx, vy, vw, vh)
        for (t in traps) t.draw(c)
        for (p in props) p.draw(c)
        mound?.draw(c)
        for (r in rocks) r.draw(c, time)
        for (cr in crates) cr.draw(c)
        for (g in gens) g.draw(c)
        table.draw(c, time)
        for (ch in chests) ch.draw(c, time)
        merchant.draw(c)
        portal.draw(c)
        for (t in trees) t.draw(c)
        for (co in coins) co.draw(c)
        for (en in enemies) if (en.isDead) en.draw(c, time)
        val py = player.y
        for (en in enemies) if (!en.isDead && en.y < py) en.draw(c, time)
        player.draw(c, time)
        for (en in enemies) if (!en.isDead && en.y >= py) en.draw(c, time)
        for (pr in projectiles) pr.draw(c)
        for (ex in explosions) ex.draw(c)
        particles.draw(c)
        for (f in floats) f.draw(c)
        c.restore()

        val dp = darkPaint
        if (dp != null && player.visionBuffTimer <= 0f) {
            val sx = (player.cx - camera.x) * zoom
            val sy = (player.cy - camera.y) * zoom
            c.save()
            c.translate(sx, sy)
            c.drawRect(-curW * 2f, -curH * 2f, curW * 2f, curH * 2f, dp)
            c.restore()
        }
        val vg = vignette
        if (vg != null && state == State.PLAYING) {
            val low = player.hp.toFloat() / max(1, player.maxHp)
            var a = 0f
            if (hurtFlash > 0f) a = hurtFlash / 0.4f * 190f
            if (low < 0.3f) a = max(a, (0.3f - low) / 0.3f * (80f + 40f * sin(time * 6f)))
            if (a > 1f) {
                vg.alpha = a.toInt().coerceIn(0, 255)
                c.drawRect(0f, 0f, curW.toFloat(), curH.toFloat(), vg)
            }
        }
    }

    private fun hpColor(f: Float): Int = when {
        f > 0.5f -> Color.parseColor("#43A047")
        f > 0.25f -> Color.parseColor("#FB8C00")
        else -> Color.parseColor("#E53935")
    }

    private fun drawHud(c: Canvas) {
        val u = Ui.u
        val w = curW.toFloat()
        val h = curH.toFloat()

        drawStatusPanel(c)

        val cw = min(560f * u, w * 0.42f)
        tmpR.set(w / 2f - cw / 2f, 10f * u, w / 2f + cw / 2f, 40f * u)
        Ui.panel(c, tmpR, Color.parseColor("#6D4C41"), Color.argb(190, 12, 16, 30), 10f)
        Ui.text(c, era.title, tmpR.centerX(), tmpR.centerY() + 6f * u, 16f, GOLD)
        tmpR.set(w / 2f - cw / 2f, 46f * u, w / 2f + cw / 2f, 92f * u)
        Ui.panel(c, tmpR, Color.parseColor("#455A64"), Color.argb(180, 10, 14, 26), 10f)
        Ui.icon(c, "ui/ico/excl.png", tmpR.right - 20f * u, tmpR.centerY(), 22f, Color.parseColor("#FFCA28"))
        Ui.wrapped(c, objectiveText(), tmpR.left + 12f * u, tmpR.top + 7f * u, cw - 54f * u, 15f, Color.WHITE, false, 2)

        val b = boss
        if (b != null && !b.isDead && b.aggro) {
            val bwid = min(520f * u, w * 0.44f)
            tmpR.set(w / 2f - bwid / 2f, 98f * u, w / 2f + bwid / 2f, 134f * u)
            Ui.panel(c, tmpR, Color.parseColor("#B71C1C"), Color.argb(200, 20, 8, 10), 10f)
            Ui.text(c, bossName(era) + if (b.shielded) "  (محصّن)" else "", tmpR.centerX(), tmpR.top + 17f * u, 15f, Color.WHITE)
            val br = RectF(tmpR.left + 12f * u, tmpR.top + 21f * u, tmpR.right - 12f * u, tmpR.bottom - 7f * u)
            Ui.bar(c, br, b.hp.toFloat() / max(1, b.maxHp), if (b.shielded) Color.parseColor("#00BCD4") else Color.parseColor("#E53935"))
        }

        val mw = 190f * u
        val mh = mw * TileMap.ROWS / TileMap.COLS
        val mx = w - 14f * u - mw
        val my = 76f * u
        tmpR.set(mx - 4f * u, my - 4f * u, mx + mw + 4f * u, my + mh + 4f * u)
        Ui.panel(c, tmpR, Color.parseColor("#455A64"), Color.argb(215, 6, 8, 16), 8f)
        val mmR = RectF(mx, my, mx + mw, my + mh)
        c.drawBitmap(world.minimap, null, mmR, null)
        val fx = mw / world.width
        val fy = mh / world.height
        for (en in enemies) {
            if (en.isDead) continue
            Ui.circle(c, mx + en.cx * fx, my + en.cy * fy, if (en.isBoss) 5f * u else 2.8f * u, if (en.isBoss) Color.parseColor("#FF1744") else Color.parseColor("#FF8A80"))
        }
        Ui.circle(c, mx + (merchant.x + 48f) * fx, my + (merchant.y + 60f) * fy, 3.4f * u, Color.parseColor("#FFD54F"))
        if (portal.isActive) Ui.circle(c, mx + (portal.x + 70f) * fx, my + (portal.y + 70f) * fy, 4.5f * u, Color.CYAN)
        Ui.circle(c, mx + player.cx * fx, my + player.cy * fy, 4f * u, Color.WHITE)
        Ui.strokeRound(c, RectF(mx + camera.x * fx, my + camera.y * fy, mx + (camera.x + camera.viewW) * fx, my + (camera.y + camera.viewH) * fy), Color.argb(150, 255, 255, 255), 1.2f, 2f)

        drawControls(c)

        if (bannerTimer > 0f && modal != Modal.DIALOGUE) {
            val a = min(1f, min(bannerTimer / 0.9f, (4.2f - bannerTimer) / 0.6f)).coerceIn(0f, 1f)
            Ui.fillRect(c, 0f, h * 0.27f, w, h * 0.27f + 170f * u, Color.argb((150 * a).toInt(), 0, 0, 0))
            Ui.text(c, era.title, w / 2f, h * 0.27f + 62f * u, 38f, Color.argb((255 * a).toInt(), 255, 213, 79))
            Ui.text(c, era.subtitle, w / 2f, h * 0.27f + 98f * u, 22f, Color.argb((230 * a).toInt(), 160, 220, 255))
            Ui.wrapped(c, era.description, w / 2f - 340f * u, h * 0.27f + 112f * u, 680f * u, 17f, Color.argb((235 * a).toInt(), 235, 235, 235), false, 2, true, false)
        }
    }

    private fun drawStatusPanel(c: Canvas) {
        val u = Ui.u
        val px = 14f * u
        val py = 14f * u
        val pw = 400f * u
        val anyMats = player.mats.any { it > 0 }
        val ph = (if (anyMats) 168f else 138f) * u
        tmpR.set(px, py, px + pw, py + ph)
        Ui.panel(c, tmpR, Color.parseColor("#B8860B"), Color.argb(205, 12, 16, 30), 12f)
        tmpR.set(px + 10f * u, py + 12f * u, px + 68f * u, py + 70f * u)
        Ui.fillRound(c, tmpR, Color.argb(210, 24, 30, 52), 10f)
        Ui.strokeRound(c, tmpR, player.weapon.color, 2.5f, 10f)
        Ui.icon(c, player.weapon.iconAsset, tmpR.centerX(), tmpR.centerY(), 42f)
        val armor = GameCatalogue.armorById(player.armorId)
        if (armor != null) Ui.icon(c, "ui/ico/shield.png", px + 58f * u, py + 62f * u, 20f, Color.parseColor("#4DD0E1"))

        val bx = px + 80f * u
        val bw = pw - 94f * u
        Ui.icon(c, "ui/ico/heart.png", bx + 10f * u, py + 26f * u, 24f, Color.parseColor("#FF5252"))
        tmpR.set(bx + 28f * u, py + 14f * u, bx + bw, py + 38f * u)
        val hf = player.hp.toFloat() / max(1, player.maxHp)
        Ui.bar(c, tmpR, hf, hpColor(hf))
        Ui.text(c, "${player.hp} / ${player.maxHp}", tmpR.centerX(), tmpR.centerY() + 5.5f * u, 15f, Color.WHITE)
        Ui.icon(c, "icons/pot10.png", bx + 10f * u, py + 54f * u, 26f)
        tmpR.set(bx + 28f * u, py + 44f * u, bx + bw, py + 62f * u)
        Ui.bar(c, tmpR, player.mana / max(1, player.maxMana), Color.parseColor("#2979FF"))
        Ui.text(c, "${player.mana.toInt()} • ${player.currentSpell.nameAr}", tmpR.centerX(), tmpR.centerY() + 5f * u, 12.5f, Color.WHITE)

        Ui.icon(c, "icons/coin.png", px + 24f * u, py + 90f * u, 28f)
        Ui.text(c, "${player.coins}", px + 44f * u, py + 98f * u, 21f, GOLD, Paint.Align.LEFT)
        var sx = px + pw - 22f * u
        if (player.hasBoat) {
            Ui.icon(c, "icons/boat.png", sx, py + 88f * u, 26f)
            sx -= 32f * u
        }
        if (player.goldKeys > 0) {
            Ui.icon(c, "icons/key_gold.png", sx - 8f * u, py + 88f * u, 24f)
            Ui.text(c, "${player.goldKeys}", sx + 6f * u, py + 94f * u, 14f, GOLD, Paint.Align.LEFT)
            sx -= 42f * u
        }
        if (player.hasPickaxe) {
            Ui.icon(c, "ui/ico/axe.png", sx, py + 88f * u, 24f, Color.parseColor("#FFB74D"))
            sx -= 32f * u
        }
        if (player.hasBossKey) Ui.icon(c, "icons/key.png", sx, py + 88f * u, 28f)

        val rx = px + 14f * u
        val ry = py + 120f * u
        val step = 62f * u
        Ui.icon(c, "icons/wood.png", rx + 8f * u, ry, 21f)
        Ui.text(c, "${player.wood}", rx + 21f * u, ry + 6f * u, 15f, Color.WHITE, Paint.Align.LEFT)
        Ui.icon(c, "icons/stone.png", rx + step + 8f * u, ry, 21f)
        Ui.text(c, "${player.stone}", rx + step + 21f * u, ry + 6f * u, 15f, Color.WHITE, Paint.Align.LEFT)
        Ui.icon(c, "icons/iron.png", rx + step * 2f + 8f * u, ry, 21f)
        Ui.text(c, "${player.iron}", rx + step * 2f + 21f * u, ry + 6f * u, 15f, Color.WHITE, Paint.Align.LEFT)
        Ui.icon(c, "icons/wool.png", rx + step * 3f + 8f * u, ry, 21f)
        Ui.text(c, "${player.wool}", rx + step * 3f + 21f * u, ry + 6f * u, 15f, Color.WHITE, Paint.Align.LEFT)
        Ui.icon(c, "icons/bomb.png", rx + step * 4f + 8f * u, ry, 21f)
        Ui.text(c, "${player.bombs}", rx + step * 4f + 21f * u, ry + 6f * u, 15f, Color.WHITE, Paint.Align.LEFT)
        Ui.icon(c, "icons/pot1.png", rx + step * 5f + 8f * u, ry, 21f)
        Ui.text(c, "${player.potions}", rx + step * 5f + 21f * u, ry + 6f * u, 15f, Color.WHITE, Paint.Align.LEFT)

        if (anyMats) {
            var mx2 = px + 22f * u
            for (i in 0 until 6) {
                if (player.mats[i] <= 0) continue
                Ui.iconTinted(c, Mats.paths[i], mx2, py + 150f * u, 24f, Mats.mul[i])
                Ui.text(c, "${player.mats[i]}", mx2 + 15f * u, py + 156f * u, 15f, Color.WHITE, Paint.Align.LEFT)
                mx2 += 58f * u
            }
        }
    }

    private fun drawControls(c: Canvas) {
        val u = Ui.u
        joystick.draw(c)
        attackBtn.draw(c)
        buildBtn.draw(c)
        dashBtn.draw(c)
        magicBtn.draw(c)
        spellSwitchBtn.draw(c)
        slowBtn.draw(c)
        bombBtn.draw(c)
        potionBtn.draw(c)
        interactBtn.draw(c)
        eatBtn.draw(c)
        bagBtn.draw(c)
        if (ow != null && !inCave) drawMinecraftHotbar(c)
        Ui.circle(c, pauseCx, pauseCy, pauseR, Color.argb(200, 14, 18, 32))
        Ui.ring(c, pauseCx, pauseCy, pauseR - 1.5f * u, Color.parseColor("#B0BEC5"), 3f * u)
        Ui.icon(c, "ui/ico/pause.png", pauseCx, pauseCy, 26f, Color.WHITE)
    }

    private fun drawMinecraftHotbar(c: Canvas) {
        val u = Ui.u
        val first = hotbarRects[0]
        val last = hotbarRects[8]
        val barBg = RectF(first.left - 6f * u, first.top - 6f * u, last.right + 6f * u, last.bottom + 6f * u)
        Ui.fillRound(c, barBg, Color.argb(220, 26, 28, 34), 8f)
        Ui.strokeRound(c, barBg, Color.rgb(90, 94, 108), 2.5f, 8f)

        val slotBmp = Assets.bmp("ui/mc_slot.png")
        val selBmp = Assets.bmp("ui/mc_slot_sel.png")
        for (i in 0 until 9) {
            val r = hotbarRects[i]
            val sel = player.hotbarIdx == i
            val bm = if (sel) selBmp else slotBmp
            if (bm != null) {
                c.drawBitmap(bm, null, r, null)
            } else {
                Ui.fillRound(c, r, if (sel) Color.rgb(160, 145, 95) else Color.rgb(110, 110, 118), 4f)
            }
            val icon = hotbarSlotIcon(i)
            val tint = if (i == 1) Color.parseColor("#FFB74D") else null
            Ui.icon(c, icon, r.centerX(), r.centerY() - 2f * u, 30f, tint)
            val cnt = hotbarSlotCount(i)
            if (cnt >= 0) {
                Ui.text(c, "$cnt", r.right - 5f * u, r.bottom - 5f * u, 12.5f, if (cnt > 0) Color.WHITE else Color.parseColor("#EF9A9A"), Paint.Align.RIGHT)
            }
            Ui.text(c, "${i + 1}", r.left + 5f * u, r.top + 13f * u, 10f, Color.rgb(210, 210, 220), Paint.Align.LEFT)
        }
        Ui.text(c, hotbarSlotTitle(player.hotbarIdx), barBg.centerX(), barBg.top - 8f * u, 14.5f, GOLD)
    }

    private fun objectiveText(): String {
        val b = boss
        if (bossDown && !portal.isActive && era == EraType.ERA_4_ISLAND) return "جد جوهرة المحيط في الصندوق خلف البوابة"
        if (bossDown) return "ادخل بوابة الزمن لتكمل رحلتك"
        if (world.gateOpen) {
            if (era == EraType.ERA_5_CYBER && b != null && b.shielded) return "عطّل المولدات لتكسر درع القائد"
            return "اهزم ${bossName(era)}"
        }
        return when (era) {
            EraType.ERA_4_ISLAND -> when {
                !player.hasBossKey && !player.hasPickaxe -> "اصنع فأساً من طاولة الصناعة (3 خشب، 2 حجر، 2 حديد)"
                !player.hasBossKey -> "احفر الكنز المدفون لتجد المفتاح"
                else -> "افتح البوابة المقفلة"
            }
            EraType.ERA_6_VOID -> {
                var left = 0
                val gateX = TileMap.GATE_COL * TileMap.TS
                for (en in enemies) if (!en.isDead && en !== b && en.x < gateX) left++
                "اهزم أشباه الفارس لفتح الحاجز ($left)"
            }
            EraType.ERA_5_CYBER -> if (!player.hasBossKey) "اهزم حامل المفتاح وعطّل المولدات" else "افتح البوابة المقفلة"
            else -> if (!player.hasBossKey) "اهزم الحارس حامل المفتاح (رمز المفتاح فوقه)" else "افتح البوابة المقفلة"
        }
    }

    private fun drawToast(c: Canvas) {
        if (toastTimer <= 0f || toastText.isEmpty()) return
        val u = Ui.u
        val a = min(1f, toastTimer / 0.4f)
        val tw = min(Ui.measure(toastText, 19f) + 50f * u, curW * 0.8f)
        val cx = curW / 2f
        val cy = curH - 78f * u
        tmpR.set(cx - tw / 2f, cy - 22f * u, cx + tw / 2f, cy + 22f * u)
        Ui.fillRound(c, tmpR, Color.argb((220 * a).toInt(), 14, 18, 34), 20f)
        Ui.strokeRound(c, tmpR, Color.argb((255 * a).toInt(), 255, 213, 79), 2f, 20f)
        Ui.text(c, toastText, cx, cy + 7f * u, 18f, Color.argb((255 * a).toInt(), 255, 255, 255))
    }

    // ---- modal frames
    private fun modalRect(): RectF {
        val w = curW.toFloat()
        val h = curH.toFloat()
        return RectF(w * 0.06f, h * 0.05f, w * 0.94f, h * 0.93f)
    }

    private fun dim(c: Canvas) {
        Ui.fillRect(c, 0f, 0f, curW.toFloat(), curH.toFloat(), Color.argb(175, 0, 0, 0))
    }

    private fun closeButton(c: Canvas, mr: RectF) {
        val u = Ui.u
        val r = RectF(mr.left + 14f * u, mr.top + 12f * u, mr.left + 62f * u, mr.top + 60f * u)
        Ui.button(c, r, "", Color.parseColor("#C62828"), true, 18f)
        Ui.icon(c, "ui/ico/x.png", r.centerX(), r.centerY(), 26f, Color.WHITE)
        hits.add(Hit(RectF(r.left - 6f * u, r.top - 6f * u, r.right + 6f * u, r.bottom + 6f * u), "close"))
    }

    private fun drawList(c: Canvas, shop: Boolean) {
        val u = Ui.u
        dim(c)
        val mr = modalRect()
        Ui.panel(c, mr, GOLD, Color.argb(246, 18, 22, 40), 16f)
        closeButton(c, mr)
        val chip = RectF(mr.left + 74f * u, mr.top + 14f * u, mr.left + 74f * u + 150f * u, mr.top + 58f * u)
        Ui.fillRound(c, chip, Color.argb(220, 8, 10, 20), 12f)
        Ui.strokeRound(c, chip, GOLD, 2f, 12f)
        Ui.icon(c, "icons/coin.png", chip.left + 24f * u, chip.centerY(), 28f)
        Ui.text(c, "${player.coins}", chip.left + 44f * u, chip.centerY() + 8f * u, 22f, GOLD, Paint.Align.LEFT)
        Ui.text(c, if (shop) "متجر إلريك" else "طاولة الصناعة والبلوكات", mr.right - 24f * u, mr.top + 46f * u, 30f, GOLD, Paint.Align.RIGHT)

        var listTop = mr.top + 78f * u
        if (shop) {
            val names = listOf("أسلحة", "دروع", "مستهلكات", "ترقيات")
            var tx = mr.right - 20f * u
            for (i in names.indices) {
                val r = RectF(tx - 150f * u, listTop, tx, listTop + 42f * u)
                val sel = i == shopTab
                Ui.button(c, r, names[i], if (sel) Color.parseColor("#F9A825") else Color.parseColor("#37474F"), true, 18f, sel)
                hits.add(Hit(RectF(r), "tab:$i"))
                tx -= 160f * u
            }
            listTop += 54f * u
        }
        listRect = RectF(mr.left + 16f * u, listTop, mr.right - 16f * u, mr.bottom - 16f * u)

        val rowH = (if (shop) 98f else 150f) * u
        val gap = 10f * u
        val shopItems: List<ShopItem> = if (shop) {
            val type = listOf(ItemType.WEAPON, ItemType.ARMOR, ItemType.CONSUMABLE, ItemType.UPGRADE)[shopTab]
            GameCatalogue.shopItems.filter { it.type == type }
        } else emptyList()
        val count = if (shop) shopItems.size else CraftingRegistry.recipes.size
        val contentH = count * (rowH + gap)
        maxScroll = max(0f, contentH - listRect.height())
        scrollY = scrollY.coerceIn(0f, maxScroll)

        c.save()
        c.clipRect(listRect)
        c.translate(0f, -scrollY)
        for (i in 0 until count) {
            val top = listRect.top + i * (rowH + gap)
            val card = RectF(listRect.left, top, listRect.right, top + rowH)
            if (shop) drawShopCard(c, shopItems[i], card) else drawCraftCard(c, CraftingRegistry.recipes[i], card)
        }
        c.restore()
        if (maxScroll > 0f) {
            val th = listRect.height() * listRect.height() / (listRect.height() + maxScroll)
            val ty = listRect.top + (listRect.height() - th) * (scrollY / maxScroll)
            tmpR.set(listRect.right - 6f * u, ty, listRect.right - 2f * u, ty + th)
            Ui.fillRound(c, tmpR, Color.argb(170, 255, 213, 79), 3f)
        }
    }

    private fun cardBase(c: Canvas, card: RectF, selected: Boolean, accent: Int) {
        val u = Ui.u
        Ui.fillRound(c, card, if (selected) Color.argb(235, 40, 50, 84) else Color.argb(225, 28, 34, 58), 12f)
        Ui.strokeRound(c, card, if (selected) GOLD else Color.argb(160, 90, 100, 140), if (selected) 3f else 1.5f, 12f)
        val box = RectF(card.right - 84f * u, card.top + 12f * u, card.right - 12f * u, card.top + 84f * u)
        Ui.fillRound(c, box, Color.argb(235, 10, 12, 24), 10f)
        Ui.strokeRound(c, box, accent, 2.5f, 10f)
    }

    private fun addCardHits(card: RectF, btn: RectF, key: String) {
        val sr = RectF(card.left, card.top - scrollY, card.right, card.bottom - scrollY)
        val br = RectF(btn.left, btn.top - scrollY, btn.right, btn.bottom - scrollY)
        if (!RectF.intersects(sr, listRect)) return
        hits.add(Hit(sr, "sel:$key"))
        hits.add(Hit(br, "buy:$key"))
    }

    private fun drawShopCard(c: Canvas, item: ShopItem, card: RectF) {
        val u = Ui.u
        val selected = selectedId == item.id
        val accent = when (item.type) {
            ItemType.WEAPON -> Color.parseColor("#FF7043")
            ItemType.ARMOR -> Color.parseColor("#4DD0E1")
            ItemType.CONSUMABLE -> Color.parseColor("#66BB6A")
            ItemType.UPGRADE -> Color.parseColor("#AB47BC")
        }
        cardBase(c, card, selected, accent)
        val boxLeft = card.right - 84f * u
        Ui.icon(c, item.iconAsset, boxLeft + 36f * u, card.top + 48f * u, 54f, item.tint)
        Ui.text(c, item.titleAr, boxLeft - 14f * u, card.top + 34f * u, 21f, Color.WHITE, Paint.Align.RIGHT)
        var desc = item.descAr
        if (item.type == ItemType.UPGRADE) desc += "  (${upgCount(item.id)}/${item.maxCount})"
        if (item.id == "potion_heal") desc += "  عندك: ${player.potions}"
        if (item.id == "bombs") desc += "  عندك: ${player.bombs}"
        if (item.id == "key_gold") desc += "  عندك: ${player.goldKeys}"
        if (stockLimit(item) > 0) desc += "  المتبقي: ${max(0, stockLeft(item))}"
        val btn = RectF(card.left + 14f * u, card.centerY() - 26f * u, card.left + 164f * u, card.centerY() + 26f * u)
        Ui.wrapped(c, desc, btn.right + 14f * u, card.top + 44f * u, boxLeft - 14f * u - (btn.right + 14f * u), 15f, Color.rgb(200, 208, 230), false, 2)
        val st = shopState(item)
        val price = priceOf(item)
        when {
            st == 1 -> {
                val label = if (item.type == ItemType.WEAPON && player.weapon.id == item.id) "مجهّز ✓" else "مملوك ✓"
                Ui.button(c, btn, label, Color.parseColor("#5D4037"), false, 18f)
            }
            st == 3 -> Ui.button(c, btn, "نفد اليوم", Color.parseColor("#455A64"), false, 18f)
            selected && st == 0 -> Ui.button(c, btn, "تأكيد الشراء", Color.parseColor("#2E7D32"), true, 17f)
            st == 0 -> {
                Ui.button(c, btn, "    $price", Color.parseColor("#F9A825"), true, 20f)
                Ui.icon(c, "icons/coin.png", btn.left + 28f * u, btn.centerY(), 26f)
            }
            else -> {
                Ui.button(c, btn, "    $price", Color.parseColor("#B71C1C"), false, 20f)
                Ui.icon(c, "icons/coin.png", btn.left + 28f * u, btn.centerY(), 26f, null, 150)
            }
        }
        addCardHits(card, btn, item.id)
    }

    private fun drawCraftCard(c: Canvas, r: CraftRecipe, card: RectF) {
        val u = Ui.u
        val selected = selectedId == r.id
        cardBase(c, card, selected, Color.parseColor("#FFB74D"))
        val boxLeft = card.right - 84f * u
        Ui.icon(c, r.iconAsset, boxLeft + 36f * u, card.top + 48f * u, 54f, r.tint)
        var title = r.titleAr
        if (r.resultType == "UPG_WEAPON") title += "  (${player.weaponLvl}/${CraftingRegistry.MAX_UPG})"
        if (r.resultType == "UPG_ARMOR") title += "  (${player.armorLvl}/${CraftingRegistry.MAX_UPG})"
        Ui.text(c, title, boxLeft - 14f * u, card.top + 32f * u, 21f, Color.WHITE, Paint.Align.RIGHT)
        val btn = RectF(card.left + 14f * u, card.centerY() - 26f * u, card.left + 164f * u, card.centerY() + 26f * u)
        Ui.wrapped(c, r.descAr, btn.right + 14f * u, card.top + 42f * u, boxLeft - 14f * u - (btn.right + 14f * u), 15f, Color.rgb(200, 208, 230), false, 2)
        val cost = costOf(r)
        val chipW = 96f * u
        var slot = 0
        fun chip(path: String, mul: Int, have: Int, need: Int) {
            if (need <= 0) return
            val row = slot / 4
            val col = slot % 4
            val cx = boxLeft - 14f * u - col * chipW
            val cy = card.bottom - 60f * u + row * 32f * u
            val ok = have >= need
            if (mul == 0) Ui.icon(c, path, cx - 12f * u, cy, 24f) else Ui.iconTinted(c, path, cx - 12f * u, cy, 24f, mul)
            Ui.text(c, "$have/$need", cx - 28f * u, cy + 6f * u, 16f, if (ok) Color.parseColor("#A5D6A7") else Color.parseColor("#EF9A9A"), Paint.Align.RIGHT)
            slot++
        }
        chip("icons/wood.png", 0, player.wood, cost.wood)
        chip("icons/stone.png", 0, player.stone, cost.stone)
        chip("icons/iron.png", 0, player.iron, cost.iron)
        chip("icons/wool.png", 0, player.wool, cost.wool)
        chip("icons/coin.png", 0, player.coins, cost.gold)
        for (i in 0 until 6) chip(Mats.paths[i], Mats.mul[i], player.mats[i], cost.mats[i])
        val st = craftState(r)
        when {
            st == 1 -> Ui.button(c, btn, "مملوك ✓", Color.parseColor("#5D4037"), false, 17f)
            selected && st == 0 -> Ui.button(c, btn, "تأكيد الصنع", Color.parseColor("#2E7D32"), true, 17f)
            st == 0 -> Ui.button(c, btn, "اصنع", Color.parseColor("#F9A825"), true, 20f)
            else -> Ui.button(c, btn, "ناقص", Color.parseColor("#B71C1C"), false, 18f)
        }
        addCardHits(card, btn, r.id)
    }

    // ---------------------------------------------------------------- Minecraft-Style Grid Inventory Modal (الحقيبة الشبكية بستايل ماين كرافت)
    private data class McSlotItem(
        val id: String,
        val title: String,
        val desc: String,
        val icon: String,
        val count: Int,
        val actionLabel: String,
        val canAct: Boolean,
        val hotbarTarget: Int = -1
    )

    private fun buildMcInventorySlots(): List<McSlotItem> {
        val list = ArrayList<McSlotItem>()
        list.add(McSlotItem("blk_wood", "بلوك ألواح خشبية", "جدار خشبي لبناء المنازل والغرف (متاح في الخانة 3 بالشريط السريع).", "icons/block_wood.png", player.blkWood, "اختر للبناء", true, 2))
        list.add(McSlotItem("blk_stone", "بلوك طوب حجري", "جدار حجري صلب لبناء القلاع والأسوار (الخانة 4 بالشريط السريع).", "icons/block_stone_brick.png", player.blkStone, "اختر للبناء", true, 3))
        list.add(McSlotItem("blk_bridge", "جسر وأرضية خشبية", "يبنى فوق الأرض أو فوق الماء كجسر (الخانة 5 بالشريط السريع).", "icons/block_bridge.png", player.blkBridge, "اختر للبناء", true, 4))
        list.add(McSlotItem("blk_door", "باب منزل خشبي", "باب يفتح ويغلق بالضغط عليه ويمنع الوحوش (الخانة 6).", "tiles/door.png", player.blkDoor, "اختر للبناء", true, 5))
        list.add(McSlotItem("blk_torch", "شعلة إضاءة", "تضيء المكان حولها ليلاً وفي الكهوف (الخانة 7).", "environment/torch.png", player.blkTorch, "اختر للبناء", true, 6))
        list.add(McSlotItem("blk_fence", "سياج دفاعي", "سياج يحمي قاعدتك ويسمح بمرور السهام والسحر (الخانة 8).", "icons/block_fence.png", player.blkFence, "اختر للبناء", true, 7))
        list.add(McSlotItem("blk_tnt", "بلوك متفجرات TNT", "ينفجر بقوة هائلة عند ضربه بالسيف أو الفأس (الخانة 9).", "icons/block_tnt.png", player.blkTnt, "اختر للبناء", true, 8))
        list.add(McSlotItem("conv_wood", "خشب خام", "اضغط لتحويل 1 خشب خام إلى 4 بلوكات ألواح خشبية للبناء!", "icons/wood.png", player.wood, "تحويل لبلوكات", player.wood >= 1))
        list.add(McSlotItem("conv_stone", "حجر خام", "اضغط لتحويل 1 حجر خام إلى 4 بلوكات طوب حجري للبناء!", "icons/stone.png", player.stone, "تحويل لبلوكات", player.stone >= 1))
        list.add(McSlotItem("raw_iron", "خام الحديد", "يستخدم في الصناعة أو يصهر في الفرن إلى ذهب وصلب.", "icons/iron.png", player.iron, "صهر بالفرن", false))
        list.add(McSlotItem("raw_wool", "صوف الخراف", "يجمع من الخراف لصناعة الأسرة والمراكب.", "icons/wool.png", player.wool, "صناعة سرير", player.wool >= 3 && player.wood >= 4))
        list.add(McSlotItem("eat_stew", "حساء الطاقة الساخن", "وجبة غنية تملأ الشبع +75 وتستعيد +35 صحة فوراً.", "icons/stew.png", player.stew, "تناول الآن", player.stew > 0))
        list.add(McSlotItem("eat_cooked", "لحم مشوي", "يملأ الشبع +45 ويستعيد +12 صحة.", "icons/cooked_meat.png", player.cookedMeat, "تناول الآن", player.cookedMeat > 0))
        list.add(McSlotItem("eat_apple", "تفاح طازج", "يملأ الشبع +16 ويستعيد +5 صحة.", "icons/apple.png", player.apples, "تناول الآن", player.apples > 0))
        list.add(McSlotItem("eat_raw", "لحم نيء", "اطبخه في الفرن مجاناً لتحويله إلى لحم مشوي وحساء!", "icons/raw_meat.png", player.rawMeat, "تناول نيئاً", player.rawMeat > 0))
        list.add(McSlotItem("use_heal", "جرعة الشفاء", "تستعيد +85 صحة فوراً.", "icons/pot1.png", player.potions, "اشرب الجرعة", player.potions > 0 && player.hp < player.maxHp))
        list.add(McSlotItem("use_speed", "إكسير السرعة والرؤية", "يمنح سرعة مضاعفة ورؤية ليلية في الكهوف لمدة 45 ثانية.", "icons/pot_speed.png", player.speedPots, "اشرب الإكسير", player.speedPots > 0))
        list.add(McSlotItem("place_bed", "سرير النوم الميداني", "ضعه في موقعك الحالي للنوم حتى الفجر وحفظ تقدمك.", "icons/bed.png", player.bedsItem, "ضع السرير هنا", player.bedsItem > 0 && ow != null && !inCave))
        list.add(McSlotItem("place_furnace", "فرن الصهر والطبخ", "ضعه في العالم لفتح واجهة الصهر والطبخ في أي مكان!", "icons/furnace.png", player.furnacesItem, "ضع الفرن هنا", player.furnacesItem > 0 && ow != null && !inCave))
        list.add(McSlotItem("place_cannon", "مدفع الحراسة الآلي", "ضعه لحماية قاعدتك؛ يطلق قذائف على الوحوش تلقائياً.", "icons/pirate_cannon.png", player.cannonsItem, "ضع المدفع هنا", player.cannonsItem > 0 && ow != null))
        list.add(McSlotItem("place_camp", "مخيم السفر السريع", "ضعه لإنشاء نقطة سفر سريع دائمة وإضاءة الليل.", "environment/bonefire.png", player.campfiresItem, "ضع المخيم هنا", player.campfiresItem > 0 && ow != null && !inCave))
        list.add(McSlotItem("use_map", "خريطة كنز مدفون", "استخدمها لتحديد موقع كنز مدفون جديد على البوصلة.", "icons/map.png", player.treasureMaps, "كشف موقع الكنز", player.treasureMaps > 0 && ow != null))
        list.add(McSlotItem("gold_keys", "مفتاح الصناديق الذهبي", "يفتح صناديق الحصون والكهوف الذهبية المقفلة.", "icons/key_gold.png", player.goldKeys, "مفتاح جاهز", false))
        for (w in GameCatalogue.weapons) {
            if (player.owned.contains(w.id)) {
                val eq = player.weapon.id == w.id
                list.add(McSlotItem("eq:${w.id}", w.nameAr, "ضرر السلاح: ${w.damage} (مستوى الصقل +${player.weaponLvl}).", w.iconAsset, 1, if (eq) "مجهّز ✓" else "تجهيز السلاح", !eq, 0))
            }
        }
        return list
    }

    private fun drawBagModal(c: Canvas) {
        val u = Ui.u
        dim(c)
        val mr = modalRect()
        // Minecraft-styled stone grey & gold border frame
        Ui.panel(c, mr, Color.rgb(190, 190, 198), Color.argb(248, 38, 40, 48), 14f)
        closeButton(c, mr)
        Ui.text(c, "حقيبة الفارس وصندوق البناء (Minecraft Style)", mr.right - 24f * u, mr.top + 44f * u, 25f, GOLD, Paint.Align.RIGHT)

        val tabs = listOf("شبكة الحقيبة (Grid)", "الصناعة والبلوكات", "السحر والمهام")
        var tx = mr.right - 20f * u
        val tabY = mr.top + 68f * u
        for (i in tabs.indices) {
            val r = RectF(tx - 190f * u, tabY, tx, tabY + 40f * u)
            val sel = i == bagTab
            Ui.button(c, r, tabs[i], if (sel) Color.parseColor("#F9A825") else Color.parseColor("#455A64"), true, 16.5f, sel)
            hits.add(Hit(RectF(r), "btab:$i"))
            tx -= 200f * u
        }

        val contentTop = tabY + 50f * u
        if (bagTab == 0) {
            // Left: Character preview & stats panel (Minecraft Inventory style!)
            val charBox = RectF(mr.left + 18f * u, contentTop, mr.left + 250f * u, mr.bottom - 18f * u)
            Ui.fillRound(c, charBox, Color.rgb(24, 26, 32), 10f)
            Ui.strokeRound(c, charBox, Color.rgb(120, 124, 136), 2.5f, 10f)
            heroSheet.update(0.016f)
            heroSheet.draw(c, charBox.centerX() - 64f * u, charBox.top + 16f * u, 128f * u, 128f * u)
            Ui.text(c, player.weapon.nameAr, charBox.centerX(), charBox.top + 158f * u, 16f, GOLD)
            Ui.text(c, "الصحة: ${player.hp}/${player.maxHp}", charBox.centerX(), charBox.top + 186f * u, 15f, Color.parseColor("#69F0AE"))
            Ui.text(c, "الشبع: ${hunger.toInt()}%  •  الذهب: ${player.coins}", charBox.centerX(), charBox.top + 212f * u, 14.5f, Color.parseColor("#FFD54F"))
            Ui.text(c, "صقل السلاح +${player.weaponLvl} • الدرع +${player.armorLvl}", charBox.centerX(), charBox.top + 238f * u, 13.5f, Color.LTGRAY)

            // Quick open Furnace / Crafting buttons inside Character Panel
            val btnCraft = RectF(charBox.left + 14f * u, charBox.bottom - 114f * u, charBox.right - 14f * u, charBox.bottom - 66f * u)
            Ui.button(c, btnCraft, "طاولة الصناعة ⚒️", Color.parseColor("#F57C00"), true, 16f)
            hits.add(Hit(btnCraft, "btab:1"))
            val btnFurn = RectF(charBox.left + 14f * u, charBox.bottom - 58f * u, charBox.right - 14f * u, charBox.bottom - 12f * u)
            Ui.button(c, btnFurn, "فتح الفرن 🔥", Color.parseColor("#D84315"), true, 16f)
            hits.add(Hit(btnFurn, "open_furnace_ui"))

            // Right: 6x4 Grid of Beveled Minecraft Slots + Bottom Selected Item Bar
            val gridLeft = charBox.right + 16f * u
            val gridRight = mr.right - 18f * u
            val cols = 6
            val rows = 4
            val gap = 8f * u
            val slotW = min(68f * u, (gridRight - gridLeft - (cols - 1) * gap) / cols)
            val slots = buildMcInventorySlots()
            if (gridSelIdx >= slots.size) gridSelIdx = 0
            val slotBmp = Assets.bmp("ui/mc_slot.png")
            val selBmp = Assets.bmp("ui/mc_slot_sel.png")

            for (idx in 0 until min(cols * rows, slots.size)) {
                val r = idx / cols
                val col = idx % cols
                val sx = gridLeft + col * (slotW + gap)
                val sy = contentTop + r * (slotW + gap)
                val sr = RectF(sx, sy, sx + slotW, sy + slotW)
                val isSel = idx == gridSelIdx
                val bm = if (isSel) selBmp else slotBmp
                if (bm != null) c.drawBitmap(bm, null, sr, null)
                else Ui.fillRound(c, sr, if (isSel) Color.rgb(170, 150, 90) else Color.rgb(110, 112, 120), 6f)

                val it = slots[idx]
                Ui.icon(c, it.icon, sr.centerX(), sr.centerY() - 3f * u, 38f)
                Ui.text(
                    c, "${it.count}", sr.right - 6f * u, sr.bottom - 6f * u, 14f,
                    if (it.count > 0) Color.WHITE else Color.parseColor("#EF9A9A"), Paint.Align.RIGHT
                )
                hits.add(Hit(sr, "gslot:$idx"))
            }

            // Selected Item Inspector Bar at bottom-right
            val selItem = slots.getOrNull(gridSelIdx)
            if (selItem != null) {
                val inspTop = contentTop + rows * (slotW + gap) + 8f * u
                val inspR = RectF(gridLeft, inspTop, gridRight, mr.bottom - 18f * u)
                Ui.fillRound(c, inspR, Color.rgb(26, 28, 36), 10f)
                Ui.strokeRound(c, inspR, GOLD, 2f, 10f)
                Ui.icon(c, selItem.icon, inspR.right - 44f * u, inspR.centerY(), 46f)
                Ui.text(c, "${selItem.title} (العدد: ${selItem.count})", inspR.right - 82f * u, inspR.top + 30f * u, 19f, GOLD, Paint.Align.RIGHT)
                Ui.wrapped(c, selItem.desc, inspR.left + 170f * u, inspR.top + 38f * u, inspR.width() - 260f * u, 14f, Color.WHITE, false, 2)
                val actBtn = RectF(inspR.left + 12f * u, inspR.centerY() - 24f * u, inspR.left + 158f * u, inspR.centerY() + 24f * u)
                Ui.button(c, actBtn, selItem.actionLabel, Color.parseColor("#2E7D32"), selItem.canAct, 16.5f)
                if (selItem.canAct) hits.add(Hit(actBtn, "gact:${selItem.id}"))
            }
            return
        }

        if (bagTab == 1) {
            // Crafting Table tab inside Bag
            listRect = RectF(mr.left + 16f * u, contentTop, mr.right - 16f * u, mr.bottom - 16f * u)
            val rowH = 146f * u
            val gap = 10f * u
            val count = CraftingRegistry.recipes.size
            val contentH = count * (rowH + gap)
            maxScroll = max(0f, contentH - listRect.height())
            scrollY = scrollY.coerceIn(0f, maxScroll)
            c.save()
            c.clipRect(listRect)
            c.translate(0f, -scrollY)
            for (i in 0 until count) {
                val top = listRect.top + i * (rowH + gap)
                val card = RectF(listRect.left, top, listRect.right, top + rowH)
                drawCraftCard(c, CraftingRegistry.recipes[i], card)
            }
            c.restore()
            return
        }

        // Tab 2: Spells & Quests
        listRect = RectF(mr.left + 16f * u, contentTop, mr.right - 16f * u, mr.bottom - 16f * u)
        data class BagEntry(val id: String, val title: String, val desc: String, val icon: String, val btnLabel: String, val enabled: Boolean, val accent: Int)
        val entries = ArrayList<BagEntry>()
        for ((idx, sp) in SpellType.values().withIndex()) {
            val active = player.spellIdx == idx
            entries.add(BagEntry("spell:$idx", "تعويذة: ${sp.nameAr}", "${sp.descAr} (طاقة: ${sp.manaCost.toInt()})", sp.iconAsset, if (active) "مفعّلة ✓" else "تفعيل", !active, sp.color))
        }
        for (q in quests) {
            val status = when {
                q.claimed -> "تم استلام المكافأة ✓"
                q.isComplete -> "جاهزة للاستلام! (+${q.rewardGold} ذهب)"
                else -> "التقدم: ${q.progress} / ${q.targetCount}  (المكافأة: ${q.rewardGold} ذهب)"
            }
            entries.add(BagEntry("quest:${q.id}", "مهمة: ${q.titleAr}", "${q.descAr} — $status", q.iconAsset, if (q.isComplete && !q.claimed) "استلم" else "${q.progress}/${q.targetCount}", q.isComplete && !q.claimed, Color.parseColor("#AB47BC")))
        }

        val rowH = 92f * u
        val gap = 10f * u
        val contentH = entries.size * (rowH + gap)
        maxScroll = max(0f, contentH - listRect.height())
        scrollY = scrollY.coerceIn(0f, maxScroll)

        c.save()
        c.clipRect(listRect)
        c.translate(0f, -scrollY)
        for ((i, e) in entries.withIndex()) {
            val top = listRect.top + i * (rowH + gap)
            val card = RectF(listRect.left, top, listRect.right, top + rowH)
            cardBase(c, card, false, e.accent)
            val boxLeft = card.right - 84f * u
            Ui.icon(c, e.icon, boxLeft + 36f * u, card.top + 46f * u, 48f)
            Ui.text(c, e.title, boxLeft - 14f * u, card.top + 32f * u, 20f, Color.WHITE, Paint.Align.RIGHT)
            val btn = RectF(card.left + 14f * u, card.centerY() - 24f * u, card.left + 154f * u, card.centerY() + 24f * u)
            Ui.wrapped(c, e.desc, btn.right + 14f * u, card.top + 40f * u, boxLeft - 14f * u - (btn.right + 14f * u), 14.5f, Color.rgb(200, 208, 230), false, 2)
            Ui.button(c, btn, e.btnLabel, e.accent, e.enabled, 17f)
            val br = RectF(btn.left, btn.top - scrollY, btn.right, btn.bottom - scrollY)
            if (RectF.intersects(br, listRect) && e.enabled) hits.add(Hit(br, e.id))
        }
        c.restore()
    }

    private fun tapBag(id: String?) {
        if (id == null) return
        if (id == "close") {
            Sound.play("click")
            modal = Modal.NONE
            return
        }
        if (id == "open_furnace_ui") {
            Sound.play("click")
            scrollY = 0f
            modal = Modal.FURNACE
            return
        }
        if (id.startsWith("btab:")) {
            bagTab = id.substring(5).toInt()
            scrollY = 0f
            selectedId = ""
            Sound.play("click")
            return
        }
        if (bagTab == 1 && (id.startsWith("sel:") || id.startsWith("buy:"))) {
            val isBuy = id.startsWith("buy:")
            val key = id.substring(4)
            if (selectedId != key) {
                selectedId = key
                Sound.play("click")
                return
            }
            if (isBuy) {
                val r = CraftingRegistry.recipes.firstOrNull { it.id == key } ?: return
                craftItem(r)
            }
            return
        }
        if (id.startsWith("gslot:")) {
            gridSelIdx = id.substring(6).toIntOrNull() ?: 0
            Sound.play("click", 0.6f)
            return
        }
        val actId = if (id.startsWith("gact:")) id.substring(5) else id
        when {
            actId == "blk_wood" -> { player.hotbarIdx = 2; modal = Modal.NONE; toast("اخترت بلوك ألواح خشبية للبناء") }
            actId == "blk_stone" -> { player.hotbarIdx = 3; modal = Modal.NONE; toast("اخترت بلوك طوب حجري للبناء") }
            actId == "blk_bridge" -> { player.hotbarIdx = 4; modal = Modal.NONE; toast("اخترت الجسر والأرضية الخشبية للبناء") }
            actId == "blk_door" -> { player.hotbarIdx = 5; modal = Modal.NONE; toast("اخترت باب المنزل الخشبي للبناء") }
            actId == "blk_torch" -> { player.hotbarIdx = 6; modal = Modal.NONE; toast("اخترت شعلة الإضاءة للبناء") }
            actId == "blk_fence" -> { player.hotbarIdx = 7; modal = Modal.NONE; toast("اخترت السياج الدفاعي للبناء") }
            actId == "blk_tnt" -> { player.hotbarIdx = 8; modal = Modal.NONE; toast("اخترت بلوك متفجرات TNT") }
            actId == "conv_wood" && player.wood >= 1 -> {
                player.wood -= 1
                player.blkWood += 4
                Sound.play("chop")
                toast("حولت 1 خشب خام إلى +4 بلوكات ألواح خشبية!")
            }
            actId == "conv_stone" && player.stone >= 1 -> {
                player.stone -= 1
                player.blkStone += 4
                Sound.play("mine")
                toast("حولت 1 حجر خام إلى +4 بلوكات طوب حجري!")
            }
            actId == "raw_wool" && player.wool >= 3 && player.wood >= 4 -> {
                player.wool -= 3
                player.wood -= 4
                player.bedsItem += 1
                Sound.play("buy")
                toast("صنعت سرير نوم ميداني من الصوف والخشب!")
            }
            actId == "eat_stew" && player.stew > 0 -> {
                player.stew--
                hunger = min(100f, hunger + 75f)
                player.hp = min(player.maxHp, player.hp + 35)
                Sound.play("potion")
                toast("تناولت حساء الطاقة الساخن (+75 شبع و+35 صحة)")
            }
            actId == "eat_cooked" && player.cookedMeat > 0 -> {
                player.cookedMeat--
                hunger = min(100f, hunger + 45f)
                player.hp = min(player.maxHp, player.hp + 12)
                Sound.play("potion")
                toast("تناولت لحماً مشوياً (+45 شبع)")
            }
            actId == "eat_apple" && player.apples > 0 -> {
                player.apples--
                hunger = min(100f, hunger + 16f)
                player.hp = min(player.maxHp, player.hp + 5)
                Sound.play("potion")
                toast("تناولت تفاحة (+16 شبع)")
            }
            actId == "eat_raw" && player.rawMeat > 0 -> {
                player.rawMeat--
                hunger = min(100f, hunger + 12f)
                Sound.play("potion")
                toast("تناولت لحماً نيئاً")
            }
            actId == "use_heal" -> {
                if (player.usePotion()) {
                    Sound.play("potion")
                    toast("استعدت +85 صحة")
                }
            }
            actId == "use_speed" -> {
                if (player.useSpeedPotion()) {
                    Sound.play("magic")
                    toast("تفعّل إكسير السرعة والرؤية الليلية!")
                    modal = Modal.NONE
                }
            }
            actId == "place_bed" && player.bedsItem > 0 -> {
                player.bedsItem--
                beds.add(Bed(player.cx - 48f, player.cy - 20f))
                Sound.play("buy")
                toast("وضعت سرير النوم في العالم!")
                modal = Modal.NONE
            }
            actId == "place_furnace" && player.furnacesItem > 0 -> {
                player.furnacesItem--
                furnaces.add(FurnaceObj(player.cx - 34f, player.cy - 24f))
                Sound.play("buy")
                toast("وضعت فرن الصهر والطبخ!")
                modal = Modal.NONE
            }
            actId == "place_cannon" && player.cannonsItem > 0 -> {
                player.cannonsItem--
                cannons.add(CannonTurret(player.cx - 38f, player.cy - 28f, true))
                Sound.play("buy")
                toast("نصبت مدفع الحراسة الآلي!")
                modal = Modal.NONE
            }
            actId == "place_camp" && player.campfiresItem > 0 -> {
                player.campfiresItem--
                campfires.add(CampfireNode(player.cx - 32f, player.cy - 24f, "مخيم الفارس ${campfires.size + 1}", true))
                Sound.play("magic")
                toast("بنيت مخيم سفر سريع جديد!")
                modal = Modal.NONE
            }
            actId == "use_map" && player.treasureMaps > 0 -> {
                player.treasureMaps--
                spawnRandomTreasureMound()
                Sound.play("era_clear")
                toast("تم تحديد موقع كنز مدفون جديد على البوصلة!")
                modal = Modal.NONE
            }
            actId.startsWith("eq:") -> {
                val wid = actId.substring(3)
                val w = GameCatalogue.weaponById(wid)
                if (w != null) {
                    player.weapon = w
                    player.hotbarIdx = 0
                    Sound.play("click")
                    toast("تم تجهيز: ${w.nameAr}")
                }
            }
            actId.startsWith("spell:") -> {
                player.spellIdx = actId.substring(6).toInt()
                Sound.play("magic")
                toast("تم تفعيل تعويذة: ${player.currentSpell.nameAr}")
            }
            actId.startsWith("quest:") -> {
                val qid = actId.substring(6)
                val q = quests.firstOrNull { it.id == qid }
                if (q != null && q.isComplete && !q.claimed) {
                    q.claimed = true
                    player.coins += q.rewardGold
                    player.mats[q.rewardMatIdx] += q.rewardMatCount
                    Sound.play("victory")
                    toast("استلمت مكافأة المهمة: +${q.rewardGold} ذهب و+${q.rewardMatCount} ${Mats.names[q.rewardMatIdx]}!")
                    saveProgress()
                }
            }
        }
    }

    // ---------------------------------------------------------------- Dedicated Minecraft-Style Furnace Modal (واجهة الفرن المستقلة)
    private fun drawFurnaceModal(c: Canvas) {
        val u = Ui.u
        dim(c)
        val mr = modalRect()
        Ui.panel(c, mr, Color.parseColor("#FF7043"), Color.argb(248, 34, 32, 38), 16f)
        closeButton(c, mr)
        Ui.text(c, "فرن الصهر والطهي (Minecraft Furnace)", mr.right - 24f * u, mr.top + 44f * u, 27f, Color.parseColor("#FFAB40"), Paint.Align.RIGHT)

        // Top visual diagram of Input + Flame + Output slots
        val diagY = mr.top + 66f * u
        val cx = mr.centerX()
        val slotBmp = Assets.bmp("ui/mc_slot.png")
        val inSlot = RectF(cx - 170f * u, diagY, cx - 110f * u, diagY + 60f * u)
        val fuelSlot = RectF(cx - 30f * u, diagY, cx + 30f * u, diagY + 60f * u)
        val outSlot = RectF(cx + 110f * u, diagY, cx + 170f * u, diagY + 60f * u)
        if (slotBmp != null) {
            c.drawBitmap(slotBmp, null, inSlot, null)
            c.drawBitmap(slotBmp, null, fuelSlot, null)
            c.drawBitmap(slotBmp, null, outSlot, null)
        }
        Ui.icon(c, "icons/raw_meat.png", inSlot.centerX(), inSlot.centerY(), 34f)
        Ui.icon(c, "environment/fire.png", fuelSlot.centerX(), fuelSlot.centerY(), 40f)
        Ui.icon(c, "icons/cooked_meat.png", outSlot.centerX(), outSlot.centerY(), 36f)
        Ui.text(c, "+", cx - 70f * u, diagY + 38f * u, 26f, GOLD)
        Ui.text(c, "➔", cx + 70f * u, diagY + 38f * u, 26f, GOLD)

        listRect = RectF(mr.left + 18f * u, diagY + 72f * u, mr.right - 18f * u, mr.bottom - 16f * u)
        data class SmeltEntry(val id: String, val title: String, val desc: String, val inIcon: String, val outIcon: String, val ok: Boolean)
        val recipes = listOf(
            SmeltEntry("f_meat", "شوي كل اللحم النيء (${player.rawMeat})", "يشوي كل اللحم النيء فوراً إلى لحم مشوي ساخن (+45 شبع).", "icons/raw_meat.png", "icons/cooked_meat.png", player.rawMeat > 0),
            SmeltEntry("f_stew", "طهي حساء الطاقة الملكي (x2)", "يتطلب (1 لحم نيء + 1 تفاحة) ➔ يعطي 2 حساء طاقة (+75 شبع و+35 صحة).", "icons/apple.png", "icons/stew.png", player.rawMeat >= 1 && player.apples >= 1),
            SmeltEntry("f_gold", "صهر الحديد والحجر إلى ذهب وصلب", "يتطلب (2 حديد + 2 حجر) ➔ يعطي +60 ذهب خالص + 1 صلب الحصن.", "icons/iron.png", "icons/coin.png", player.iron >= 2 && player.stone >= 2),
            SmeltEntry("f_glass", "صهر الصخور إلى زجاج بلوري (x6)", "يتطلب (2 حجر + 1 خشب) ➔ يعطي 6 بلوكات زجاج بلوري للبناء.", "icons/stone.png", "icons/block_glass.png", player.stone >= 2 && player.wood >= 1),
            SmeltEntry("f_tnt", "تقطير البارود ومتفجرات TNT (x3)", "يتطلب (2 حجر + 1 حديد + 15 ذهب) ➔ يعطي 3 بلوكات متفجرات TNT.", "icons/bomb.png", "icons/block_tnt.png", player.stone >= 2 && player.iron >= 1 && player.coins >= 15)
        )

        val rowH = 86f * u
        val gap = 10f * u
        val contentH = recipes.size * (rowH + gap)
        maxScroll = max(0f, contentH - listRect.height())
        scrollY = scrollY.coerceIn(0f, maxScroll)

        c.save()
        c.clipRect(listRect)
        c.translate(0f, -scrollY)
        for ((i, r) in recipes.withIndex()) {
            val top = listRect.top + i * (rowH + gap)
            val card = RectF(listRect.left, top, listRect.right, top + rowH)
            cardBase(c, card, false, Color.parseColor("#FF7043"))
            val boxLeft = card.right - 84f * u
            Ui.icon(c, r.outIcon, boxLeft + 36f * u, card.top + 43f * u, 46f)
            Ui.text(c, r.title, boxLeft - 14f * u, card.top + 32f * u, 19.5f, Color.WHITE, Paint.Align.RIGHT)
            Ui.text(c, r.desc, boxLeft - 14f * u, card.top + 60f * u, 14f, Color.rgb(210, 215, 230), Paint.Align.RIGHT)
            val btn = RectF(card.left + 14f * u, card.centerY() - 24f * u, card.left + 160f * u, card.centerY() + 24f * u)
            Ui.button(c, btn, if (r.ok) "اصهر / اطبخ 🔥" else "موارد ناقصة", Color.parseColor("#E65100"), r.ok, 17f)
            val br = RectF(btn.left, btn.top - scrollY, btn.right, btn.bottom - scrollY)
            if (RectF.intersects(br, listRect) && r.ok) hits.add(Hit(br, r.id))
        }
        c.restore()
    }

    private fun tapFurnaceModal(id: String?) {
        if (id == null) return
        if (id == "close") {
            Sound.play("click")
            modal = Modal.NONE
            return
        }
        when (id) {
            "f_meat" -> if (player.rawMeat > 0) {
                val n = player.rawMeat
                player.rawMeat = 0
                player.cookedMeat += n
                Sound.play("potion")
                toast("الفرن: شويت $n قطعة لحم مشوي!")
                advanceQuest("q_cook", n)
                saveProgress()
            }
            "f_stew" -> if (player.rawMeat >= 1 && player.apples >= 1) {
                player.rawMeat -= 1
                player.apples -= 1
                player.stew += 2
                Sound.play("potion")
                toast("الفرن: طهوت 2 حساء طاقة ملكي!")
                advanceQuest("q_cook", 2)
                saveProgress()
            }
            "f_gold" -> if (player.iron >= 2 && player.stone >= 2) {
                player.iron -= 2
                player.stone -= 2
                player.coins += 60
                player.mats[0] += 1
                Sound.play("coin")
                toast("الفرن: صهرت الخامات إلى +60 ذهب و+1 صلب الحصن!")
                advanceQuest("q_cook", 1)
                saveProgress()
            }
            "f_glass" -> if (player.stone >= 2 && player.wood >= 1) {
                player.stone -= 2
                player.wood -= 1
                player.blkGlass += 6
                Sound.play("mine")
                toast("الفرن: صنعت +6 بلوكات زجاج بلوري للبناء!")
                advanceQuest("q_cook", 1)
                saveProgress()
            }
            "f_tnt" -> if (player.stone >= 2 && player.iron >= 1 && player.coins >= 15) {
                player.stone -= 2
                player.iron -= 1
                player.coins -= 15
                player.blkTnt += 3
                Sound.play("buy")
                toast("الفرن: صنعت +3 بلوكات متفجرات TNT!")
                advanceQuest("q_cook", 1)
                saveProgress()
            }
        }
    }

    // ---------------------------------------------------------------- Minecraft-Style Villager Multi-Trade & Steal Modal
    private fun drawVillagerModal(c: Canvas) {
        val u = Ui.u
        val v = targetVillager ?: return
        dim(c)
        val mr = modalRect()
        Ui.panel(c, mr, v.role.color, Color.argb(248, 28, 32, 42), 16f)
        closeButton(c, mr)
        Ui.text(c, "${v.nameAr} • ${v.role.titleAr} (المستوى ${v.level}/5)", mr.right - 24f * u, mr.top + 44f * u, 25f, GOLD, Paint.Align.RIGHT)

        // Villager XP Bar + Steal Button on top bar
        val xpBar = RectF(mr.right - 340f * u, mr.top + 56f * u, mr.right - 24f * u, mr.top + 70f * u)
        Ui.bar(c, xpBar, v.xp.toFloat() / max(1, v.level * 2), Color.parseColor("#69F0AE"))

        val stealChance = if (player.owned.contains("dagger_silver")) 80 else 60
        val canSteal = v.stealCd <= 0f
        val rSteal = RectF(mr.left + 80f * u, mr.top + 14f * u, mr.left + 350f * u, mr.top + 58f * u)
        Ui.button(
            c, rSteal,
            if (canSteal) "سرقة الجيب ($stealChance%)" else "القروي حذر حالياً",
            Color.parseColor("#C62828"), canSteal, 16.5f
        )
        if (canSteal) hits.add(Hit(rSteal, "v_steal"))

        listRect = RectF(mr.left + 18f * u, mr.top + 82f * u, mr.right - 18f * u, mr.bottom - 16f * u)
        val bonus = v.level - 1
        data class TradeOffer(val id: String, val giveTitle: String, val getTitle: String, val giveIcon: String, val getIcon: String, val ok: Boolean)
        val offers = listOf(
            TradeOffer("t_special", v.tradeGiveText, "${v.tradeGetText} (+مكافأة المستوى $bonus)", "icons/wool.png", "icons/coin.png", true),
            TradeOffer("t_blocks", "3 خشب خام + 2 حجر", "10 بلوكات بناء مشكلة (خشب وطوب وجسور)", "icons/wood.png", "icons/block_wood.png", player.wood >= 3 && player.stone >= 2),
            TradeOffer("t_stew", "35 ذهب", "${2 + bonus} حساء الطاقة الساخن + ${2 + bonus} تفاح", "icons/coin.png", "icons/stew.png", player.coins >= 35),
            TradeOffer("t_key", "3 حديد + 30 ذهب", "1 مفتاح ذهبي + 2 قنبلة", "icons/iron.png", "icons/key_gold.png", player.iron >= 3 && player.coins >= 30),
            TradeOffer("t_blacksmith", "فتح ورشة الحدادة والصناعة", "صقل الأسلحة والدروع وصناعة البلوكات", "ui/ico/axe.png", "icons/sabre.png", true)
        )

        val rowH = 84f * u
        val gap = 10f * u
        val contentH = offers.size * (rowH + gap)
        maxScroll = max(0f, contentH - listRect.height())
        scrollY = scrollY.coerceIn(0f, maxScroll)

        c.save()
        c.clipRect(listRect)
        c.translate(0f, -scrollY)
        for ((i, o) in offers.withIndex()) {
            val top = listRect.top + i * (rowH + gap)
            val card = RectF(listRect.left, top, listRect.right, top + rowH)
            cardBase(c, card, false, v.role.color)
            val boxLeft = card.right - 84f * u
            Ui.icon(c, o.getIcon, boxLeft + 36f * u, card.top + 42f * u, 44f)
            Ui.text(c, "${o.giveTitle}   ➔   ${o.getTitle}", boxLeft - 14f * u, card.top + 36f * u, 19f, Color.WHITE, Paint.Align.RIGHT)
            Ui.text(c, "اضغط لإتمام المقايضة ورفع مستوى القروي", boxLeft - 14f * u, card.top + 62f * u, 13.5f, Color.rgb(190, 210, 225), Paint.Align.RIGHT)
            val btn = RectF(card.left + 14f * u, card.centerY() - 24f * u, card.left + 160f * u, card.centerY() + 24f * u)
            Ui.button(c, btn, if (o.id == "t_blacksmith") "الحدادة ⚒️" else "مقايضة 🤝", Color.parseColor("#2E7D32"), o.ok, 17f)
            val br = RectF(btn.left, btn.top - scrollY, btn.right, btn.bottom - scrollY)
            if (RectF.intersects(br, listRect) && o.ok) hits.add(Hit(br, o.id))
        }
        c.restore()
    }

    private fun tapVillagerModal(id: String?) {
        val v = targetVillager ?: return
        when (id) {
            "close" -> {
                Sound.play("click")
                modal = Modal.NONE
            }
            "t_blacksmith" -> openCraft()
            "t_special" -> {
                var ok = false
                val bonus = v.level - 1
                when (v.tradeId) {
                    "wool_gold" -> if (player.wool >= 2) { player.wool -= 2; player.coins += 45 + bonus * 8; ok = true }
                    "meat_stew" -> if (player.rawMeat >= 2) { player.rawMeat -= 2; player.stew += 2 + bonus; ok = true }
                    "iron_key" -> if (player.iron >= 3) { player.iron -= 3; player.goldKeys += 1; player.coins += bonus * 10; ok = true }
                    "wood_pot" -> if (player.wood >= 4) { player.wood -= 4; player.potions += 2 + bonus; ok = true }
                    "gold_mat" -> if (player.coins >= 55) { player.coins -= 55; val b = (ow?.biomeAtPx(v.x, v.y) ?: 0).coerceIn(0, 5); player.mats[b] += 2 + bonus; ok = true }
                    "stone_bomb" -> if (player.stone >= 4) { player.stone -= 4; player.bombs += 3 + bonus; ok = true }
                }
                if (ok) {
                    v.addTradeXp()
                    Sound.play("buy")
                    toast("تم التبادل بنجاح مع ${v.nameAr}! (مستوى القروي: ${v.level})")
                    advanceQuest("q_trade", 1)
                    saveProgress()
                } else {
                    Sound.play("deny")
                    toast("لا تملك الموارد المطلوبة لهذا العرض")
                }
            }
            "t_blocks" -> if (player.wood >= 3 && player.stone >= 2) {
                player.wood -= 3
                player.stone -= 2
                player.blkWood += 4
                player.blkStone += 4
                player.blkBridge += 2
                v.addTradeXp()
                Sound.play("buy")
                toast("حصلت على 10 بلوكات بناء من القروي!")
                advanceQuest("q_trade", 1)
                saveProgress()
            }
            "t_stew" -> if (player.coins >= 35) {
                val bonus = v.level - 1
                player.coins -= 35
                player.stew += 2 + bonus
                player.apples += 2 + bonus
                v.addTradeXp()
                Sound.play("buy")
                toast("اشتريت وجبات حساء وتفاح طازج!")
                advanceQuest("q_trade", 1)
                saveProgress()
            }
            "t_key" -> if (player.iron >= 3 && player.coins >= 30) {
                player.iron -= 3
                player.coins -= 30
                player.goldKeys += 1
                player.bombs += 2
                v.addTradeXp()
                Sound.play("buy")
                toast("قايضت الحديد بـ 1 مفتاح ذهبي و2 قنبلة!")
                advanceQuest("q_trade", 1)
                saveProgress()
            }
            "v_steal" -> {
                if (v.stealCd > 0f) return
                v.stealCd = 45f
                val chance = if (player.owned.contains("dagger_silver")) 0.80f else 0.60f
                if (rnd.nextFloat() < chance) {
                    val stolenGold = 35 + rnd.nextInt(45)
                    player.coins += stolenGold
                    if (rnd.nextFloat() < 0.35f) player.goldKeys++
                    Sound.play("coin")
                    toast("نجحت السرقة! حصلت على +$stolenGold ذهب بدون أن يشعر")
                    advanceQuest("q_trade", 1)
                    modal = Modal.NONE
                } else {
                    Sound.play("boss")
                    shake = 10f
                    toast("فشلت السرقة! حراس القرية يطاردونك!")
                    modal = Modal.NONE
                    for (k in 0..1) {
                        val gx = v.x + (if (k == 0) -90f else 90f)
                        val gy = v.y + 40f
                        val guard = Enemy(gx, gy, EnemyRole.SPEARMAN, EraType.ERA_1_MEDIEVAL)
                        guard.aggro = true
                        enemies.add(guard)
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- Fast Travel Modal (البوابات الزمنية والمخيمات)
    private fun drawTravelModal(c: Canvas) {
        val u = Ui.u
        dim(c)
        val mr = modalRect()
        Ui.panel(c, mr, Color.parseColor("#00E5FF"), Color.argb(246, 14, 20, 38), 16f)
        closeButton(c, mr)
        Ui.text(c, "شبكة البوابات الزمنية والمخيمات (سفر سريع)", mr.right - 24f * u, mr.top + 46f * u, 26f, Color.parseColor("#80DEEA"), Paint.Align.RIGHT)

        listRect = RectF(mr.left + 16f * u, mr.top + 74f * u, mr.right - 16f * u, mr.bottom - 16f * u)
        data class Dest(val name: String, val sub: String, val x: Float, val y: Float, val icon: String)
        val dests = ArrayList<Dest>()
        val w = ow ?: return
        dests.add(Dest("قرية الزمن المركزية", "المركز الآمن • المتجر والحداد والفرن", w.spawnX, w.spawnY, "environment/magic_circle.png"))
        for ((idx, v) in w.subVillageTiles.withIndex()) {
            dests.add(Dest("قرية ${biomeNames[idx]}", "بوابة القرية الفرعية والقرويين", v.first * OpenWorld.TS, (v.second + 2) * OpenWorld.TS, "environment/magic_circle2.png"))
        }
        for (cp in campfires) {
            if (cp.isLit) dests.add(Dest(cp.nameAr, "نقطة مخيم مضيئة", cp.x, cp.y + 48f, "environment/bonefire.png"))
        }

        val rowH = 86f * u
        val gap = 10f * u
        val contentH = dests.size * (rowH + gap)
        maxScroll = max(0f, contentH - listRect.height())
        scrollY = scrollY.coerceIn(0f, maxScroll)

        c.save()
        c.clipRect(listRect)
        c.translate(0f, -scrollY)
        for ((i, d) in dests.withIndex()) {
            val top = listRect.top + i * (rowH + gap)
            val card = RectF(listRect.left, top, listRect.right, top + rowH)
            cardBase(c, card, false, Color.parseColor("#00BCD4"))
            val boxLeft = card.right - 84f * u
            Ui.icon(c, d.icon, boxLeft + 36f * u, card.top + 43f * u, 46f)
            Ui.text(c, d.name, boxLeft - 14f * u, card.top + 32f * u, 20f, Color.WHITE, Paint.Align.RIGHT)
            Ui.text(c, d.sub, boxLeft - 14f * u, card.top + 60f * u, 15f, Color.rgb(180, 210, 230), Paint.Align.RIGHT)
            val btn = RectF(card.left + 14f * u, card.centerY() - 24f * u, card.left + 164f * u, card.centerY() + 24f * u)
            Ui.button(c, btn, "انتقل فوراً", Color.parseColor("#00838F"), true, 18f)
            val br = RectF(btn.left, btn.top - scrollY, btn.right, btn.bottom - scrollY)
            if (RectF.intersects(br, listRect)) hits.add(Hit(br, "tp:$i"))
        }
        c.restore()
    }

    private fun tapTravelModal(id: String?) {
        if (id == null) return
        if (id == "close") {
            Sound.play("click")
            modal = Modal.NONE
            return
        }
        if (id.startsWith("tp:")) {
            val idx = id.substring(3).toIntOrNull() ?: return
            val w = ow ?: return
            val coords = ArrayList<Pair<Float, Float>>()
            val names = ArrayList<String>()
            coords.add(Pair(w.spawnX, w.spawnY))
            names.add("قرية الزمن المركزية")
            for ((bi, v) in w.subVillageTiles.withIndex()) {
                coords.add(Pair(v.first * OpenWorld.TS, (v.second + 2) * OpenWorld.TS))
                names.add("قرية ${biomeNames[bi]}")
            }
            for (cp in campfires) {
                if (cp.isLit) {
                    coords.add(Pair(cp.x, cp.y + 48f))
                    names.add(cp.nameAr)
                }
            }
            if (idx in coords.indices) {
                player.x = coords[idx].first
                player.y = coords[idx].second
                camera.snapTo(player.cx, player.cy)
                w.warm(player.cx, player.cy)
                w.reveal(player.cx, player.cy, 20)
                fade = 1f
                modal = Modal.NONE
                Sound.play("portal")
                toast("انتقلت عبر الزمن إلى: ${names[idx]}")
            }
        }
    }

    private fun drawPause(c: Canvas) {
        val u = Ui.u
        dim(c)
        val w = curW.toFloat()
        val h = curH.toFloat()
        val pr = RectF(w / 2f - 230f * u, h / 2f - 280f * u, w / 2f + 230f * u, h / 2f + 280f * u)
        Ui.panel(c, pr, GOLD, Color.argb(248, 18, 22, 40), 16f)
        Ui.text(c, "إيقاف مؤقت", pr.centerX(), pr.top + 52f * u, 30f, GOLD)
        val bw = 340f * u
        var y = pr.top + 78f * u
        val items = listOf(
            Triple("resume", "متابعة اللعب", Color.parseColor("#2E7D32")),
            Triple("bag", "فتح الحقيبة وصندوق البناء", Color.parseColor("#8D6E63")),
            Triple("sfx", "المؤثرات: " + (if (Sound.sfxOn) "تشغيل" else "إيقاف"), Color.parseColor("#1565C0")),
            Triple("music", "الموسيقى: " + (if (Sound.musicOn) "تشغيل" else "إيقاف"), Color.parseColor("#6A1B9A")),
            Triple("quality", "الجودة: " + (if (lowQuality) "منخفضة (أسرع)" else "عالية"), Color.parseColor("#00838F")),
            Triple("title", "حفظ والعودة للقائمة", Color.parseColor("#C62828"))
        )
        for (item in items) {
            val r = RectF(pr.centerX() - bw / 2f, y, pr.centerX() + bw / 2f, y + 54f * u)
            Ui.button(c, r, item.second, item.third, true, 19f)
            hits.add(Hit(r, item.first))
            y += 66f * u
        }
    }

    private fun drawDialogue(c: Canvas) {
        val u = Ui.u
        val w = curW.toFloat()
        val h = curH.toFloat()
        if (dialogueIdx >= dialogue.size) return
        val line = dialogue[dialogueIdx]
        val pr = RectF(w * 0.06f, h - 236f * u, w * 0.94f, h - 20f * u)
        Ui.panel(c, pr, GOLD, Color.argb(240, 14, 18, 34), 16f)
        val box = RectF(pr.right - 150f * u, pr.top + 18f * u, pr.right - 18f * u, pr.top + 150f * u)
        Ui.fillRound(c, box, Color.argb(255, 8, 10, 22), 12f)
        Ui.strokeRound(c, box, GOLD, 2.5f, 12f)
        val bm = Assets.bmp(line.portrait)
        if (bm != null) {
            val sw = if (bm.width > bm.height * 3 / 2) bm.height else bm.width
            val src = android.graphics.Rect(0, 0, sw, bm.height)
            val dstR = RectF(box.left + 10f * u, box.top + 10f * u, box.right - 10f * u, box.bottom - 10f * u)
            c.drawBitmap(bm, src, dstR, null)
        }
        val nr = RectF(pr.right - 330f * u, pr.top - 20f * u, pr.right - 160f * u, pr.top + 14f * u)
        Ui.fillRound(c, nr, Color.parseColor("#F9A825"), 12f)
        Ui.text(c, line.speaker, nr.centerX(), nr.centerY() + 6f * u, 18f, Color.parseColor("#3E2723"), Paint.Align.CENTER, true, false)
        val shown = min(line.text.length, dialogueChars())
        val txt = line.text.substring(0, shown)
        Ui.wrapped(c, txt, pr.left + 28f * u, pr.top + 28f * u, pr.width() - 200f * u, 23f, Color.WHITE, false, 5, false, false)
        if (shown >= line.text.length) {
            val a = (160 + 90 * sin(time * 5f)).toInt().coerceIn(0, 255)
            Ui.text(c, "اضغط للمتابعة", pr.left + 28f * u, pr.bottom - 20f * u, 15f, Color.argb(a, 255, 213, 79), Paint.Align.LEFT)
        }
        hits.add(Hit(RectF(0f, 0f, w, h), "adv"))
    }

    // ---------------------------------------------------------------- open world + caves
    private fun saveProgress() {
        if (ow != null) saveOpen() else player.save(era.ordinal)
    }

    private fun saveOpen() {
        val w = ow ?: return
        player.save(5)
        val saveX = if (inCave) surfaceX else player.x
        val saveY = if (inCave) surfaceY else player.y
        SaveStore.putAll(
            mapOf<String, Any>(
                "mode" to "open", "ow_day" to dayNo, "ow_time" to dayTime,
                "ow_px" to saveX, "ow_py" to saveY,
                "ow_exp" to w.saveExplored(), "ow_mod" to w.saveMods(),
                "ow_pblocks" to w.savePlayerBlocks(),
                "ow_unlocked" to true, "ow_hunger" to hunger
            )
        )
    }

    private fun isNight(): Boolean = sin(6.2832f * dayTime) < -0.15f

    private fun darknessNow(): Float {
        if (player.visionBuffTimer > 0f) return 0.05f
        if (inCave) return 0.82f
        return (0.30f - sin(6.2832f * dayTime) * 0.75f).coerceIn(0f, 0.88f)
    }

    private fun timeLabel(): String = when {
        inCave -> "داخل الكهف (طابق $caveFloor)"
        dayTime < 0.06f -> "فجر"
        dayTime < 0.38f -> "نهار"
        dayTime < 0.50f -> "غروب"
        else -> "ليل"
    }

    private fun spawnRandomTreasureMound() {
        val w = ow ?: return
        for (tries in 0 until 40) {
            val tx = 30 + rnd.nextInt(OpenWorld.N - 60)
            val ty = 30 + rnd.nextInt(OpenWorld.N - 60)
            val px = tx * OpenWorld.TS
            val py = ty * OpenWorld.TS
            if (!w.isWaterAt(px, py) && w.freeAt(px, py)) {
                mounds.add(BuriedMound(px, py))
                break
            }
        }
    }

    private fun enterCave(ce: CaveEntrance, floorNo: Int) {
        if (!inCave) {
            surfaceX = player.x
            surfaceY = player.y
        }
        inCave = true
        activeCave = ce
        caveFloor = floorNo.coerceIn(1, 3)
        val cEra = EraType.values()[ce.biome.coerceIn(0, 5)]
        val cm = TileMap(cEra, ce.seed + caveFloor * 131L)
        caveMap = cm

        enemies.clear()
        projectiles.clear()
        explosions.clear()
        coins.clear()
        chests.clear()
        crates.clear()
        rocks.clear()
        trees.clear()
        traps.clear()
        cocoons.clear()
        totems.clear()
        boss = null
        bossDown = false

        val sx = 4f * TileMap.TS
        val sy = 15f * TileMap.TS
        caveExitSurface = CaveExitNode(sx - 20f, sy - 90f, false, caveFloor)
        val nextX = 34f * TileMap.TS
        val nextY = 15f * TileMap.TS
        caveExitNext = if (caveFloor < 3) CaveExitNode(nextX, nextY, true, caveFloor) else null

        val pts = ArrayList<Pt>()
        pts.add(Pt(sx, sy))
        pts.add(Pt(nextX, nextY))

        for (i in 0 until (5 + caveFloor * 2)) {
            val rx = (7 + rnd.nextInt(26)) * TileMap.TS
            val ry = (5 + rnd.nextInt(20)) * TileMap.TS
            rocks.add(MiningRock(rx, ry, i % 2 == 0))
            pts.add(Pt(rx, ry))
        }
        for (i in 0 until (3 + caveFloor)) {
            val tx = (8 + rnd.nextInt(24)) * TileMap.TS
            val ty = (5 + rnd.nextInt(20)) * TileMap.TS
            traps.add(DungeonTrap(tx, ty, i % 2 == 0))
            pts.add(Pt(tx, ty))
        }
        for (i in 0 until (2 + caveFloor)) {
            val ccx = (10 + rnd.nextInt(20)) * TileMap.TS
            val ccy = (6 + rnd.nextInt(18)) * TileMap.TS
            cocoons.add(CaveCocoon(ccx, ccy, ce.biome))
            pts.add(Pt(ccx, ccy))
        }
        val ttx = 20f * TileMap.TS
        val tty = 8f * TileMap.TS
        totems.add(CaveTotem(ttx, tty, ce.biome))
        pts.add(Pt(ttx, tty))

        chests.add(TreasureChest(18f * TileMap.TS, 22f * TileMap.TS, 45 * caveFloor, false, false))
        chests.add(TreasureChest(32f * TileMap.TS, 8f * TileMap.TS, 85 * caveFloor, false, true))
        pts.add(Pt(18f * TileMap.TS, 22f * TileMap.TS))
        pts.add(Pt(32f * TileMap.TS, 8f * TileMap.TS))

        for (i in 0 until (4 + caveFloor * 2)) {
            val ex = (9 + rnd.nextInt(24)) * TileMap.TS
            val ey = (5 + rnd.nextInt(20)) * TileMap.TS
            val role = if (i % 3 == 0) EnemyRole.HEAVY else if (i % 2 == 0) EnemyRole.GHOUL else EnemyRole.SPEARMAN
            enemies.add(Enemy(ex, ey, role, cEra))
            pts.add(Pt(ex, ey))
        }
        if (caveFloor == 3) {
            val cb = Enemy(31f * TileMap.TS, 15f * TileMap.TS, EnemyRole.BOSS, cEra)
            boss = cb
            enemies.add(cb)
            pts.add(Pt(cb.x, cb.y))
        }

        cm.buildCave(caveFloor, Pt(sx, sy), pts)
        player.x = sx
        player.y = sy
        player.inBoat = false
        camera = Camera2D(cm.width, cm.height)
        camera.viewW = curW / zoom
        camera.viewH = curH / zoom
        camera.snapTo(player.cx, player.cy)
        buildDark()
        fade = 1f
        Sound.play("portal")
        Sound.playMusic(2)
        toast("دخلت ${ce.nameAr} — الطابق $caveFloor")
    }

    private fun exitCaveToSurface() {
        val w = ow ?: return
        inCave = false
        caveMap = null
        activeCave = null
        enemies.clear()
        projectiles.clear()
        rocks.clear()
        traps.clear()
        cocoons.clear()
        totems.clear()
        chests.clear()
        boss = null
        player.x = surfaceX
        player.y = surfaceY
        camera = Camera2D(w.width, w.height)
        camera.viewW = curW / zoom
        camera.viewH = curH / zoom
        camera.snapTo(player.cx, player.cy)
        w.warm(player.cx, player.cy)
        buildDark()
        fade = 1f
        Sound.play("portal")
        Sound.playMusic(if (isNight()) 2 else 1)
        toast("خرجت إلى سطح العالم المفتوح")
        populateOpenWorldLandmarks(w)
    }

    private fun populateOpenWorldLandmarks(w: OpenWorld) {
        chests.clear()
        banners.clear()
        for ((bi, f) in w.fortressTiles.withIndex()) {
            val fx = f.first * OpenWorld.TS
            val fy = f.second * OpenWorld.TS
            banners.add(FortressBanner(fx - 36f, fy - 5f * OpenWorld.TS, "حصن ${biomeNames[bi]}"))
            chests.add(TreasureChest(fx - 32f, fy - 32f, 140 + bi * 30, false, true))
            chests.add(TreasureChest(fx + 64f, fy - 32f, 65 + bi * 15, false, false))
        }
        for (isl in w.islandCenters) {
            val ix = isl.first * OpenWorld.TS
            val iy = isl.second * OpenWorld.TS
            banners.add(FortressBanner(ix, iy - 64f, "جزيرة القراصنة"))
            chests.add(TreasureChest(ix + 48f, iy, 120, false, true))
        }
    }

    private fun startOpen(restore: Boolean) {
        val w = OpenWorld(20260)
        ow = w
        inCave = false
        caveMap = null
        activeCave = null
        state = State.PLAYING
        modal = Modal.NONE
        enemies.clear()
        projectiles.clear()
        explosions.clear()
        floats.clear()
        coins.clear()
        chests.clear()
        crates.clear()
        rocks.clear()
        trees.clear()
        traps.clear()
        props.clear()
        gens.clear()
        beds.clear()
        furnaces.clear()
        campfires.clear()
        caveEntrances.clear()
        cocoons.clear()
        totems.clear()
        cannons.clear()
        banners.clear()
        docks.clear()
        mounds.clear()
        sheeps.clear()
        villagers.clear()
        owPortals.clear()
        particles.list.clear()
        mound = null
        boss = null
        bossDown = false
        greeted = false
        cancelAll()
        shopBought.clear()

        val ts = OpenWorld.TS
        val cxp = OpenWorld.CX * ts
        val cyp = OpenWorld.CY * ts

        merchant = MerchantNPC(cxp - 5f * ts, cyp - 2f * ts)
        table = CraftTable(cxp + 4f * ts, cyp - 2f * ts)
        beds.add(Bed((OpenWorld.CX - 7) * ts, (OpenWorld.CY - 6) * ts))
        furnaces.add(FurnaceObj(cxp + 4f * ts, cyp + 2f * ts))
        owPortals.add(TimePortal(cxp - 70f, cyp - 6f * ts, "بوابة الزمن المركزية").apply { isActive = true })
        cannons.add(CannonTurret(cxp - 8f * ts, cyp + 6f * ts, true))
        cannons.add(CannonTurret(cxp + 7f * ts, cyp + 6f * ts, true))

        villagers.add(VillagerNPC(cxp + 2.5f * ts, cyp - 2.5f * ts, "الحداد براندون", VillagerRole.BLACKSMITH, "3 حديد ➔ مفتاح ذهبي", "مفتاح ذهبي", "iron_key"))
        villagers.add(VillagerNPC(cxp - 2.5f * ts, cyp + 2f * ts, "الحكيم أوريون", VillagerRole.ELDER, "55 ذهب ➔ 2 خام عصر", "خامات نادرة", "gold_mat"))
        villagers.add(VillagerNPC(cxp - 6f * ts, cyp + 3f * ts, "المزارع توماس", VillagerRole.FARMER, "2 صوف ➔ 45 ذهب", "45 ذهب", "wool_gold"))
        villagers.add(VillagerNPC(cxp + 6f * ts, cyp + 3f * ts, "الطاهية هيلين", VillagerRole.TRADER, "2 لحم نيء ➔ 2 حساء", "حساء الطاقة", "meat_stew"))

        val caveNames = arrayOf(
            "كهف القلعة المظلم", "كهف الكريستال العميق", "سرداب الزومبي",
            "مغارة القراصنة", "مختبر السايبر تحت الأرض", "هاوية الفراغ الكوني"
        )
        for (bi in 0 until 6) {
            val sv = w.subVillageTiles[bi]
            val vx = sv.first * ts
            val vy = sv.second * ts
            beds.add(Bed(vx - 5f * ts, vy - 5f * ts))
            furnaces.add(FurnaceObj(vx + 3f * ts, vy - 2f * ts))
            campfires.add(CampfireNode(vx, vy, "مخيم ${biomeNames[bi]}", true))
            owPortals.add(TimePortal(vx - 70f, vy + 4f * ts, "بوابة ${biomeNames[bi]}").apply { isActive = true })
            villagers.add(
                VillagerNPC(
                    vx - 2f * ts, vy + 1f * ts,
                    "تاجر ${biomeNames[bi].substringBefore(" ")}",
                    if (bi == 3) VillagerRole.PIRATE_SMUGGLER else VillagerRole.TRADER,
                    if (bi % 2 == 0) "4 خشب ➔ 2 جرعة شفاء" else "4 حجر ➔ 3 قنابل",
                    "مقايضة القرية",
                    if (bi % 2 == 0) "wood_pot" else "stone_bomb"
                )
            )

            val cv = w.caveTiles[bi]
            caveEntrances.add(CaveEntrance(cv.first * ts - 64f, cv.second * ts - 56f, bi, caveNames[bi], 5000L + bi * 99L))

            val ft = w.fortressTiles[bi]
            val fx = ft.first * ts
            val fy = ft.second * ts
            cannons.add(CannonTurret(fx - 4f * ts, fy + 5f * ts, false))
            cannons.add(CannonTurret(fx + 3f * ts, fy + 5f * ts, false))
        }

        for (isl in w.islandCenters) {
            docks.add(BoatDock((isl.first - 12) * ts, isl.second * ts))
            cannons.add(CannonTurret(isl.first * ts - 80f, isl.second * ts + 80f, false))
        }
        docks.add(BoatDock((OpenWorld.CX - 22) * ts, (OpenWorld.CY + 18) * ts))

        populateOpenWorldLandmarks(w)

        for (i in 0 until 28) {
            val ang = rnd.nextFloat() * 6.2832f
            val rad = (12f + rnd.nextFloat() * 140f) * ts
            val sx = cxp + cos(ang) * rad
            val sy = cyp + sin(ang) * rad
            if (sx in 400f..(w.width - 400f) && sy in 400f..(w.height - 400f) && !w.isWaterAt(sx, sy) && w.freeAt(sx, sy)) {
                sheeps.add(Sheep(sx, sy))
            }
        }

        repeat(6) { spawnRandomTreasureMound() }

        eyes.clear()
        stalker = null
        hunger = 85f
        sinceHurt = 10f
        hungerWarned = false
        props.add(WorldProp("environment/fire.png", cxp - 28f, cyp - 40f, 56f, 56f))
        props.add(WorldProp("environment/torch.png", cxp - 3f * ts, cyp - 3f * ts, 36f, 36f, 0.15f))
        props.add(WorldProp("environment/torch.png", cxp + 3f * ts, cyp - 3f * ts, 36f, 36f, 0.15f))
        player.resetState()
        player.x = w.spawnX
        player.y = w.spawnY
        surfaceX = player.x
        surfaceY = player.y
        dayNo = 1
        dayTime = 0.05f
        if (restore) {
            dayNo = SaveStore.int("ow_day", 1)
            dayTime = SaveStore.float("ow_time", 0.05f)
            hunger = SaveStore.float("ow_hunger", 85f)
            w.loadMods(SaveStore.str("ow_mod", ""))
            w.loadPlayerBlocks(SaveStore.str("ow_pblocks", ""))
            w.loadExplored(SaveStore.str("ow_exp", ""))
            val sx = SaveStore.float("ow_px", w.spawnX)
            val sy = SaveStore.float("ow_py", w.spawnY)
            if (!w.blocked(sx + 33f, sy + 66f, sx + 63f, sy + 88f)) {
                player.x = sx
                player.y = sy
            }
        }
        camera = Camera2D(w.width, w.height)
        camera.viewW = curW / zoom
        camera.viewH = curH / zoom
        camera.snapTo(player.cx, player.cy)
        w.warm(player.cx, player.cy)
        w.reveal(player.cx, player.cy, 20)
        nightFlag = isNight()
        owSpawnTimer = 4f
        owSaveTimer = 0f
        owExploreTimer = 0f
        owRegrowTimer = 0f
        deathTimer = 0f
        fade = 1f
        bannerTimer = 0f
        Sound.playMusic(if (nightFlag) 2 else 1)
        saveOpen()
        if (!restore) openDialogue(Story.openWorldIntro()) { }
    }

    private fun respawnOpen() {
        val w = ow ?: return
        if (inCave) {
            exitCaveToSurface()
        }
        val lost = player.coins / 5
        player.coins -= lost
        player.resetState()
        player.x = w.spawnX
        player.y = w.spawnY
        enemies.clear()
        projectiles.clear()
        camera.snapTo(player.cx, player.cy)
        deathTimer = 0f
        fade = 1f
        hunger = max(hunger, 60f)
        eyes.clear()
        stalker = null
        toast("استيقظتَ في القرية المركزية... فقدتَ $lost ذهب")
    }

    private fun updateOpen(dt: Float) {
        val w = ow ?: return
        val edt = if (player.slowTimer > 0f) dt * 0.3f else dt
        val hpBefore = player.hp

        dayTime += dt / dayLength
        if (dayTime >= 1f) {
            dayTime -= 1f
            dayNo++
            shopBought.clear()
            toast("بداية اليوم $dayNo")
            w.regrow(dayNo, player.cx, player.cy)
            saveOpen()
        }
        val nowNight = isNight()
        if (nowNight != nightFlag) {
            nightFlag = nowNight
            if (!inCave) Sound.playMusic(if (nowNight) 2 else 1)
            toast(if (nowNight) "حلّ الليل... الزومبي والظلام يتحركون!" else "طلع الفجر")
            if (!nowNight) {
                val old = stalker
                if (old != null) enemies.remove(old)
                stalker = null
                eyes.clear()
            }
        }
        hunger = max(0f, hunger - dt * (if (nowNight) 0.22f else 0.16f))
        sinceHurt += dt
        if (hunger < 25f && !hungerWarned) {
            hungerWarned = true
            toast("أنت جائع... تناول طعاماً من زر الأكل أو الحقيبة")
        }
        if (hunger > 40f) hungerWarned = false

        if (nowNight && !inCave) {
            eyeTimer -= dt
            if (eyeTimer <= 0f) {
                eyeTimer = 3f + rnd.nextFloat() * 4f
                val maxE = if (lowQuality) 1 else 3
                if (eyes.size < maxE && !w.inVillage(player.cx, player.cy)) {
                    val a = rnd.nextFloat() * 6.2832f
                    val r = 380f + rnd.nextFloat() * 120f
                    eyes.add(Eyes(player.cx + cos(a) * r, player.cy + sin(a) * r, 9f, rnd.nextFloat() * 6f))
                }
            }
        }
        var ei = eyes.size - 1
        while (ei >= 0) {
            val ey = eyes[ei]
            ey.life -= dt
            if (ey.life <= 0f || !nowNight || inCave) {
                eyes.removeAt(ei)
            } else if (dist(ey.x, ey.y, player.cx, player.cy) < 250f) {
                Sound.play("whisper", 0.7f)
                particles.burst(ey.x, ey.y, 6, Color.parseColor("#B71C1C"), 90f, 4f, 0.5f)
                eyes.removeAt(ei)
            }
            ei--
        }

        if (attackBtn.isPressed && !player.isDead) {
            if (player.attack()) Sound.play("swing", 0.7f)
        }

        if (!inCave) {
            w.allowDeepWater = player.hasBoat
            w.allowDoorPass = true
            player.update(dt, joystick.dx, joystick.dy, w)
            w.allowDeepWater = false
            w.allowDoorPass = false
            player.inBoat = player.hasBoat && w.isWaterAt(player.cx, player.cy)
        } else {
            player.inBoat = false
            player.update(dt, joystick.dx, joystick.dy, collider)
        }

        camera.update(player.cx, player.cy, dt)
        if (player.isDead) {
            deathTimer += dt
            if (deathTimer > 2f) {
                respawnOpen()
                return
            }
        }
        resolveMelee()

        if (!inCave) {
            spawnOpenEnemies(w, dt)
            for (sh in sheeps) sh.update(dt, w)
            for (vl in villagers) vl.update(dt, w)
            for (fn in furnaces) fn.update(dt)
            for (cp in campfires) cp.update(dt)
            for (pt in owPortals) pt.update(dt)
            for (m in mounds) m.update(dt)
            for (cn in cannons) {
                if (dist(cn.x, cn.y, player.cx, player.cy) < 1200f) {
                    if (cn.update(dt, player, enemies, projectiles)) Sound.play("explode", 0.5f)
                }
            }
        } else {
            for (t in traps) t.update(dt, player)
            for (r in rocks) r.update(dt)
            for (cc in cocoons) {
                if (cc.update(dt) && enemies.size < 14) {
                    val b = cc.biome.coerceIn(0, 5)
                    val en = Enemy(cc.x, cc.y + 40f, EnemyRole.GHOUL, EraType.values()[b])
                    en.aggro = true
                    enemies.add(en)
                }
            }
        }

        var i = 0
        while (i < enemies.size) {
            enemies[i].update(edt, player, collider, projectiles)
            i++
        }
        separateEnemies()
        val eit = enemies.iterator()
        while (eit.hasNext()) {
            val en = eit.next()
            val far = !inCave && dist(en.cx, en.cy, player.cx, player.cy) > 2100f
            if ((en.isDead && en.deadTimer > 2.5f) || (!en.isDead && far)) eit.remove()
        }

        var pi = projectiles.size - 1
        while (pi >= 0) {
            val pr = projectiles[pi]
            pr.update(edt)
            if (!pr.dead) updateProjectile(pr)
            if (pr.dead) {
                if (pr.kind == Projectile.BOMB || pr.kind == Projectile.CANNONBALL) explode(pr.x, pr.y)
                projectiles.removeAt(pi)
            }
            pi--
        }
        var xi = explosions.size - 1
        while (xi >= 0) {
            explosions[xi].update(dt)
            if (explosions[xi].done) explosions.removeAt(xi)
            xi--
        }
        particles.update(dt)
        var fi = floats.size - 1
        while (fi >= 0) {
            floats[fi].update(dt)
            if (floats[fi].dead) floats.removeAt(fi)
            fi--
        }
        var ci = coins.size - 1
        while (ci >= 0) {
            val co = coins[ci]
            co.update(dt, player)
            if (co.collected) {
                player.coins += co.value
                addFloat("+${co.value}", player.cx, player.y - 10f, Color.parseColor("#FFD740"), 24f)
                Sound.play("coin", 0.7f)
                coins.removeAt(ci)
            }
            ci--
        }
        merchant.update(dt)
        for (p in props) p.update(dt)

        if (!inCave) {
            owExploreTimer -= dt
            if (owExploreTimer <= 0f) {
                owExploreTimer = 0.6f
                w.reveal(player.cx, player.cy, 18)
            }
            owRegrowTimer += dt
            if (owRegrowTimer > 40f) {
                owRegrowTimer = 0f
                w.regrow(dayNo, player.cx, player.cy)
                if (sheeps.count { !it.isDead } < 20) {
                    val sx = w.spawnX + (rnd.nextFloat() - 0.5f) * 3500f
                    val sy = w.spawnY + (rnd.nextFloat() - 0.5f) * 3500f
                    if (!w.isWaterAt(sx, sy) && w.freeAt(sx, sy)) sheeps.add(Sheep(sx, sy))
                }
            }
        }
        owSaveTimer += dt
        if (owSaveTimer > 25f) {
            owSaveTimer = 0f
            saveOpen()
        }
        if (player.hp < hpBefore) {
            onPlayerHurt(hpBefore - player.hp)
            sinceHurt = 0f
        }
        if (!player.isDead) {
            if (hunger > 50f && sinceHurt > 5f && player.hp < player.maxHp) {
                regenTimer += dt
                if (regenTimer >= 1.5f) {
                    regenTimer = 0f
                    player.hp = min(player.maxHp, player.hp + 2)
                }
            }
            if (hunger <= 0f) {
                starveTimer += dt
                if (starveTimer >= 2.5f) {
                    starveTimer = 0f
                    if (player.hp > 8) {
                        player.hp -= 2
                        addFloat("جوع!", player.cx, player.y - 10f, Color.parseColor("#FF8A65"), 22f)
                    }
                }
            }
        }
        updateTargetOpen()
        updateButtons()
    }

    private fun updateTargetOpen() {
        val px = player.cx
        val py = player.cy
        var best = Target.NONE
        var bestD = 1e9f
        targetChest = null
        targetMound = null
        targetCave = null
        targetCamp = null
        targetTotem = null
        targetVillager = null

        fun cand(t: Target, d: Float, lim: Float): Boolean {
            if (d < lim && d < bestD) {
                best = t
                bestD = d
                return true
            }
            return false
        }

        for (c in chests) {
            if (c.isNear(px, py)) {
                val d = dist(px, py, c.x + 32f, c.y + 32f)
                if (cand(Target.CHEST, d, 105f)) targetChest = c
            }
        }

        if (inCave) {
            if (caveExitSurface.isNear(px, py)) cand(Target.CAVE_EXIT, dist(px, py, caveExitSurface.x + 48f, caveExitSurface.y + 48f), 110f)
            val nxt = caveExitNext
            if (nxt != null && nxt.isNear(px, py)) cand(Target.CAVE_NEXT, dist(px, py, nxt.x + 48f, nxt.y + 48f), 110f)
            for (tt in totems) {
                if (tt.isNear(px, py)) {
                    val d = dist(px, py, tt.x + 44f, tt.y + 48f)
                    if (cand(Target.TOTEM, d, 110f)) targetTotem = tt
                }
            }
            target = best
            interactBtn.visible = best != Target.NONE && !player.isDead
            styleInteractButton(best)
            return
        }

        cand(Target.SHOP, dist(px, py, merchant.x + 48f, merchant.y + 60f), 130f)
        cand(Target.CRAFT, dist(px, py, table.x + 32f, table.y + 32f), 112f)
        cand(Target.COOK, dist(px, py, OpenWorld.CX * OpenWorld.TS, OpenWorld.CY * OpenWorld.TS - 12f), 105f)

        for (b in beds) if (b.isNear(px, py)) cand(Target.SLEEP, dist(px, py, b.x + 48f, b.y + 32f), 105f)
        for (fn in furnaces) if (fn.isNear(px, py)) cand(Target.FURNACE, dist(px, py, fn.x + 34f, fn.y + 34f), 110f)
        for (cp in campfires) {
            if (cp.isNear(px, py)) {
                val d = dist(px, py, cp.x + 32f, cp.y + 32f)
                if (cand(Target.CAMPFIRE, d, 115f)) targetCamp = cp
            }
        }
        for (ce in caveEntrances) {
            if (ce.isNear(px, py)) {
                val d = dist(px, py, ce.x + 64f, ce.y + 70f)
                if (cand(Target.CAVE_ENTER, d, 125f)) targetCave = ce
            }
        }
        for (vl in villagers) {
            if (vl.isNear(px, py)) {
                val d = dist(px, py, vl.x + 44f, vl.y + 50f)
                if (cand(Target.VILLAGER, d, 120f)) targetVillager = vl
            }
        }
        for (pt in owPortals) {
            if (pt.isNear(px, py)) cand(Target.PORTAL, dist(px, py, pt.x + 70f, pt.y + 70f), 115f)
        }
        for (dk in docks) {
            if (dk.isNear(px, py)) cand(Target.BOAT_DOCK, dist(px, py, dk.x + 40f, dk.y + 40f), 120f)
        }
        for (m in mounds) {
            if (m.isNear(px, py)) {
                val d = dist(px, py, m.x + 32f, m.y + 32f)
                if (cand(Target.MOUND, d, 100f)) targetMound = m
            }
        }

        target = best
        interactBtn.visible = best != Target.NONE && !player.isDead
        styleInteractButton(best)
    }

    private fun foodCount(): Int = player.stew + player.cookedMeat + player.apples + player.rawMeat

    private fun eatBest() {
        if (ow == null) return
        if (hunger >= 98f) {
            toast("لست جائعاً")
            Sound.play("deny", 0.5f)
            return
        }
        if (player.stew > 0) {
            player.stew--
            hunger = min(100f, hunger + 75f)
            player.hp = min(player.maxHp, player.hp + 35)
            addFloat("+75 شبع  +35 صحة", player.cx, player.y - 8f, Color.parseColor("#FFD54F"), 24f)
        } else if (player.cookedMeat > 0) {
            player.cookedMeat--
            hunger = min(100f, hunger + 45f)
            player.hp = min(player.maxHp, player.hp + 12)
            addFloat("+45 شبع", player.cx, player.y - 8f, Color.parseColor("#FFB74D"), 24f)
        } else if (player.apples > 0) {
            player.apples--
            hunger = min(100f, hunger + 16f)
            player.hp = min(player.maxHp, player.hp + 5)
            addFloat("+16 شبع", player.cx, player.y - 8f, Color.parseColor("#FFB74D"), 24f)
        } else if (player.rawMeat > 0) {
            player.rawMeat--
            hunger = min(100f, hunger + 12f)
            if (player.hp > 12) player.hp -= 4
            addFloat("لحم نيء! (اطبخه في الفرن أفضل)", player.cx, player.y - 8f, Color.parseColor("#EF9A9A"), 22f)
        } else {
            toast("لا تملك طعاماً")
            Sound.play("deny", 0.5f)
            return
        }
        Sound.play("potion", 0.8f)
    }

    private fun cookAll(freeFuel: Boolean) {
        val n = player.rawMeat
        if (n <= 0) {
            toast("ليس معك لحم نيء للطبخ")
            Sound.play("deny", 0.6f)
            return
        }
        val need = if (freeFuel) 0 else (n + 1) / 2
        if (player.wood < need) {
            toast("تحتاج $need خشب للنار (أو استخدم الفرن مجاناً)")
            Sound.play("deny", 0.6f)
            return
        }
        player.wood -= need
        player.rawMeat = 0
        player.cookedMeat += n
        Sound.play("potion")
        toast("طبخت $n قطعة لحم مشوي!")
        advanceQuest("q_cook", n)
        saveProgress()
    }

    private fun trySleep() {
        val w = ow ?: return
        if (dayTime < 0.42f) {
            toast("لا تستطيع النوم الآن... انتظر المساء أو الليل")
            Sound.play("deny", 0.6f)
            return
        }
        for (en in enemies) {
            if (!en.isDead && dist(en.cx, en.cy, player.cx, player.cy) < 650f) {
                toast("الأعداء قريبون! اقضِ عليهم قبل النوم")
                Sound.play("deny", 0.6f)
                return
            }
        }
        dayTime = 0.04f
        dayNo++
        shopBought.clear()
        w.regrow(dayNo, player.cx, player.cy)
        player.hp = player.maxHp
        player.mana = player.maxMana.toFloat()
        hunger = max(15f, hunger - 20f)
        enemies.clear()
        projectiles.clear()
        eyes.clear()
        stalker = null
        nightFlag = false
        Sound.playMusic(1)
        fade = 1f
        toast("نمتَ في السرير حتى الفجر... بداية اليوم $dayNo")
        Sound.play("potion")
        saveOpen()
    }

    private fun pickRole(b: Int, night: Boolean): EnemyRole {
        val r = rnd.nextFloat()
        return when (b) {
            0 -> if (night && r < 0.35f) EnemyRole.GHOUL else if (r < 0.4f) EnemyRole.MELEE else if (r < 0.75f) EnemyRole.SPEARMAN else EnemyRole.ARCHER
            1 -> if (r < 0.5f) EnemyRole.GHOUL else if (r < 0.8f) EnemyRole.SPEARMAN else EnemyRole.HEAVY
            2 -> if (r < 0.45f) EnemyRole.MELEE else if (r < 0.85f) EnemyRole.GHOUL else EnemyRole.HEAVY
            3 -> if (r < 0.55f) EnemyRole.TRIBAL else if (r < 0.80f) EnemyRole.ARCHER else EnemyRole.SPEARMAN
            4 -> if (r < 0.7f) EnemyRole.COMMANDO else EnemyRole.HEAVY
            else -> if (r < 0.6f) EnemyRole.CLONE else EnemyRole.COMMANDO
        }
    }

    private fun spawnOpenEnemies(w: OpenWorld, dt: Float) {
        owSpawnTimer -= dt
        if (owSpawnTimer > 0f) return
        val night = isNight()
        owSpawnTimer = if (night) 2.0f else 4.8f
        if (night && dayNo >= 2 && stalker == null && rnd.nextFloat() < 0.14f && !w.inVillage(player.cx, player.cy, 1.4f)) {
            for (t in 0 until 10) {
                val a = rnd.nextFloat() * 6.2832f
                val r = 900f + rnd.nextFloat() * 120f
                val ex = player.cx + cos(a) * r
                val ey = player.cy + sin(a) * r
                if (ex < 200f || ey < 200f || ex > w.width - 200f || ey > w.height - 200f) continue
                if (!w.freeAt(ex, ey)) continue
                val st = Enemy(ex - 46f, ey - 64f, EnemyRole.CLONE, EraType.ERA_6_VOID)
                if (w.blocked(st.x + st.fOffX, st.y + st.fOffY, st.x + st.fOffX + st.fW, st.y + st.fOffY + st.fH)) continue
                st.aggro = true
                enemies.add(st)
                stalker = st
                toast("شيء ما يطاردك في الظلام...")
                Sound.play("boss", 0.9f)
                return
            }
        }
        var alive = 0
        for (en in enemies) if (!en.isDead) alive++
        val cap = (if (lowQuality) 5 else 9) + (if (night) 4 else 0)
        if (alive >= cap) return
        for (t in 0 until 12) {
            val a = rnd.nextFloat() * 6.2832f
            val r = 680f + rnd.nextFloat() * 320f
            val ex = player.cx + cos(a) * r
            val ey = player.cy + sin(a) * r
            if (ex < 200f || ey < 200f || ex > w.width - 200f || ey > w.height - 200f) continue
            if (w.inVillage(ex, ey, 1.25f)) continue
            if (!w.freeAt(ex, ey)) continue
            val b = w.biomeAtPx(ex, ey)
            val eraForSpawn = if (night && rnd.nextFloat() < 0.4f) EraType.ERA_3_ZOMBIE else EraType.values()[b]
            val en = Enemy(ex - 42f, ey - 60f, pickRole(b, night), eraForSpawn)
            if (w.blocked(en.x + en.fOffX, en.y + en.fOffY, en.x + en.fOffX + en.fW, en.y + en.fOffY + en.fH)) continue
            en.aggro = night
            enemies.add(en)
            break
        }
    }

    private fun meleeTiles(w: OpenWorld) {
        val ts = OpenWorld.TS
        val c0 = floor(atkRect.left / ts).toInt()
        val c1 = floor(atkRect.right / ts).toInt()
        val r0 = floor(atkRect.top / ts).toInt()
        val r1 = floor(atkRect.bottom / ts).toInt()
        for (r in r0..r1) for (cc in c0..c1) {
            if (cc < 0 || r < 0 || cc >= OpenWorld.N || r >= OpenWorld.N) continue
            val i = r * OpenWorld.N + cc
            if (!w.breakable(i)) continue
            val key = i + 10000000
            if (player.swingHit.contains(key)) continue
            player.swingHit.add(key)
            hitOpenTile(w, i)
        }
    }

    private fun openExplode(w: OpenWorld, x: Float, y: Float) {
        val ts = OpenWorld.TS
        val c0 = ((x - 160f) / ts).toInt()
        val c1 = ((x + 160f) / ts).toInt()
        val r0 = ((y - 160f) / ts).toInt()
        val r1 = ((y + 160f) / ts).toInt()
        for (r in r0..r1) for (cc in c0..c1) {
            if (cc < 0 || r < 0 || cc >= OpenWorld.N || r >= OpenWorld.N) continue
            val i = r * OpenWorld.N + cc
            if (!w.breakable(i)) continue
            if (dist(cc * ts + 32f, r * ts + 32f, x, y) > 150f) continue
            hitOpenTile(w, i)
            hitOpenTile(w, i)
        }
    }

    private fun hitOpenTile(w: OpenWorld, i: Int) {
        val tx = i % OpenWorld.N
        val ty = i / OpenWorld.N
        val px = tx * OpenWorld.TS + 32f
        val py = ty * OpenWorld.TS + 32f

        // Check if hitting a player-placed Minecraft block first!
        val pb = w.blockCodeAtTile(tx, ty)
        if (pb != 0) {
            val removed = w.removeBlockAt(tx, ty)
            if (removed != null) {
                if (removed == BlockType.TNT_BLOCK) {
                    explode(px, py)
                } else {
                    player.addBlockCount(removed, 1)
                    Sound.play("chop", 0.8f)
                    particles.burst(px, py, 8, Color.parseColor("#FFE082"), 150f, 5f, 0.4f)
                    addFloat("+1 ${removed.nameAr}", px, py - 20f, Color.parseColor("#FFE082"), 20f)
                }
            }
            return
        }

        val k = w.kindAt(i)
        val b = w.biome[i].toInt()
        val res = w.damageTile(i, dayNo)
        if (res == 0) return
        val broke = res == 2
        val n = if (broke) 2 else 1
        if (k == OpenWorld.TREE) {
            Sound.play("chop", 0.8f)
            player.wood += n
            addFloat("+$n خشب", px, py - 24f, Color.parseColor("#FFCC80"), 22f)
            particles.burst(px, py, 6, Color.parseColor("#66BB6A"), 140f, 5f, 0.4f)
            if (broke && (b == 0 || b == 3) && rnd.nextFloat() < 0.30f) {
                player.apples++
                addFloat("+1 تفاحة", px, py - 46f, Color.parseColor("#FF8A80"), 22f)
            }
        } else if (k == OpenWorld.ROCK) {
            Sound.play("mine", 0.8f)
            player.stone += n
            addFloat("+$n حجر", px, py - 24f, Color.parseColor("#CFD8DC"), 22f)
            if (broke && rnd.nextFloat() < 0.40f) {
                player.iron += 1
                addFloat("+1 حديد", px, py - 46f, Color.parseColor("#80D8FF"), 22f)
            }
            particles.burst(px, py, 6, Color.parseColor("#B0BEC5"), 150f, 5f, 0.4f)
        } else if (k == OpenWorld.ORE) {
            Sound.play("mine", 0.9f)
            player.mats[b] += n
            addFloat("+$n ${Mats.names[b]}", px, py - 24f, Color.parseColor("#FFD740"), 22f)
            if (broke) player.iron += 1
            particles.burst(px, py, 8, OpenWorld.ORE_MUL[b], 170f, 5f, 0.5f)
        }
    }

    private fun drawOpenWorld(c: Canvas) {
        val w = ow ?: return
        val sh = shake
        val ox = if (sh > 0f) (rnd.nextFloat() - 0.5f) * sh * 2f else 0f
        val oy = if (sh > 0f) (rnd.nextFloat() - 0.5f) * sh * 2f else 0f
        c.save()
        c.scale(zoom, zoom)
        c.translate(-camera.x + ox, -camera.y + oy)
        val vx = camera.x - 24f
        val vy = camera.y - 24f
        val vw = camera.viewW + 48f
        val vh = camera.viewH + 48f

        if (inCave && caveMap != null) {
            val cm = caveMap!!
            cm.draw(c, vx, vy, vw, vh, time)
            cm.drawDecor(c, vx, vy, vw, vh)
            caveExitSurface.draw(c, time)
            caveExitNext?.draw(c, time)
            for (t in traps) t.draw(c)
            for (r in rocks) r.draw(c, time)
            for (cc in cocoons) cc.draw(c, time)
            for (tt in totems) tt.draw(c, time)
            for (ch in chests) ch.draw(c, time)
        } else {
            w.draw(c, vx, vy, vw, vh)
            // Draw Minecraft-style tile placement / breaker highlight cursor in front of player
            if (player.hotbarIdx >= 1 && !player.isDead) {
                val (tx, ty) = aimTileCoords()
                val rx = tx * OpenWorld.TS
                val ry = ty * OpenWorld.TS
                tmpR.set(rx + 2f, ry + 2f, rx + OpenWorld.TS - 2f, ry + OpenWorld.TS - 2f)
                val blk = player.selectedBlockType()
                val canPlace = blk != null && w.canPlaceBlockAt(tx, ty, blk, player.cx, player.cy)
                val col = when {
                    player.hotbarIdx == 1 -> Color.argb(180, 255, 193, 7)
                    canPlace -> Color.argb(180, 105, 240, 174)
                    else -> Color.argb(160, 255, 82, 82)
                }
                Ui.fillRound(c, tmpR, Color.argb(45, Color.red(col), Color.green(col), Color.blue(col)), 4f)
                screenPaint.style = Paint.Style.STROKE
                screenPaint.strokeWidth = 3f
                screenPaint.color = col
                c.drawRect(tmpR, screenPaint)
                screenPaint.style = Paint.Style.FILL
            }
            w.drawTrees(c, vx, vy, vw, vh)
            for (dk in docks) if (abs(dk.x - player.cx) < vw && abs(dk.y - player.cy) < vh) dk.draw(c, time)
            for (m in mounds) if (abs(m.x - player.cx) < vw && abs(m.y - player.cy) < vh) m.draw(c)
            for (b in beds) if (abs(b.x - player.cx) < vw && abs(b.y - player.cy) < vh) b.draw(c)
            for (fn in furnaces) if (abs(fn.x - player.cx) < vw && abs(fn.y - player.cy) < vh) fn.draw(c, time)
            for (cp in campfires) if (abs(cp.x - player.cx) < vw && abs(cp.y - player.cy) < vh) cp.draw(c, time)
            for (ce in caveEntrances) if (abs(ce.x - player.cx) < vw && abs(ce.y - player.cy) < vh) ce.draw(c, time)
            for (pt in owPortals) if (abs(pt.x - player.cx) < vw && abs(pt.y - player.cy) < vh) pt.draw(c)
            for (bn in banners) if (abs(bn.x - player.cx) < vw && abs(bn.y - player.cy) < vh) bn.draw(c, time)
            for (ch in chests) if (abs(ch.x - player.cx) < vw && abs(ch.y - player.cy) < vh) ch.draw(c, time)
            for (cn in cannons) if (abs(cn.x - player.cx) < vw && abs(cn.y - player.cy) < vh) cn.draw(c)
            for (p in props) p.draw(c)
            table.draw(c, time)
            merchant.draw(c)
            for (sh in sheeps) if (abs(sh.x - player.cx) < vw && abs(sh.y - player.cy) < vh) sh.draw(c)
            for (vl in villagers) if (abs(vl.x - player.cx) < vw && abs(vl.y - player.cy) < vh) vl.draw(c)
        }

        for (co in coins) co.draw(c)
        for (en in enemies) if (en.isDead) en.draw(c, time)
        val pyy = player.y
        for (en in enemies) if (!en.isDead && en.y < pyy) en.draw(c, time)
        player.draw(c, time)
        for (en in enemies) if (!en.isDead && en.y >= pyy) en.draw(c, time)
        for (pr in projectiles) pr.draw(c)
        for (ex in explosions) ex.draw(c)
        particles.draw(c)
        for (f in floats) f.draw(c)
        c.restore()

        val np = nightPaint
        val dk = darknessNow()
        if (np != null && dk > 0.03f) {
            np.alpha = (dk * 255f).toInt().coerceIn(0, 255)
            val sx = (player.cx - camera.x) * zoom
            val sy = (player.cy - camera.y) * zoom
            c.save()
            c.translate(sx, sy)
            c.drawRect(-curW * 2f, -curH * 2f, curW * 2f, curH * 2f, np)
            c.restore()
        }
        if (dk > 0.4f && !inCave) {
            val uu = Ui.u
            for (ey in eyes) {
                val ex = (ey.x - camera.x) * zoom
                val eyy = (ey.y - camera.y) * zoom
                if (ex < -20f || ex > curW + 20f || eyy < -20f || eyy > curH + 20f) continue
                if (sin(time * 2.2f + ey.seed) < -0.6f) continue
                val a = (210 * min(1f, ey.life / 2f)).toInt().coerceIn(0, 255)
                Ui.circle(c, ex - 7f * uu, eyy, 3.2f * uu, Color.argb(a, 255, 60, 40))
                Ui.circle(c, ex + 7f * uu, eyy, 3.2f * uu, Color.argb(a, 255, 60, 40))
            }
        }
        val vg = vignette
        if (vg != null) {
            val low = player.hp.toFloat() / max(1, player.maxHp)
            var a = 0f
            if (hurtFlash > 0f) a = hurtFlash / 0.4f * 190f
            if (low < 0.3f) a = max(a, (0.3f - low) / 0.3f * (80f + 40f * sin(time * 6f)))
            if (a > 1f) {
                vg.alpha = a.toInt().coerceIn(0, 255)
                c.drawRect(0f, 0f, curW.toFloat(), curH.toFloat(), vg)
            }
        }
    }

    private fun drawOpenHud(c: Canvas) {
        val w = ow ?: return
        val u = Ui.u
        val sw = curW.toFloat()
        drawStatusPanel(c)
        val phh = (if (player.mats.any { it > 0 }) 168f else 138f) * u
        val hy = 14f * u + phh + 8f * u
        tmpR.set(14f * u, hy, 414f * u, hy + 38f * u)
        Ui.panel(c, tmpR, Color.parseColor("#BF360C"), Color.argb(205, 12, 16, 30), 10f)
        Ui.icon(c, "icons/apple.png", 34f * u, hy + 19f * u, 22f)
        val hb = RectF(50f * u, hy + 10f * u, 180f * u, hy + 28f * u)
        Ui.bar(c, hb, hunger / 100f, if (hunger > 40f) Color.parseColor("#FB8C00") else Color.parseColor("#E53935"))
        var fx = 196f * u
        Ui.icon(c, "icons/apple.png", fx, hy + 19f * u, 20f)
        Ui.text(c, "${player.apples}", fx + 12f * u, hy + 25f * u, 14f, Color.WHITE, Paint.Align.LEFT)
        fx += 50f * u
        Ui.icon(c, "icons/raw_meat.png", fx, hy + 19f * u, 20f)
        Ui.text(c, "${player.rawMeat}", fx + 12f * u, hy + 25f * u, 14f, Color.WHITE, Paint.Align.LEFT)
        fx += 50f * u
        Ui.icon(c, "icons/cooked_meat.png", fx, hy + 19f * u, 20f)
        Ui.text(c, "${player.cookedMeat}", fx + 12f * u, hy + 25f * u, 14f, Color.WHITE, Paint.Align.LEFT)
        fx += 50f * u
        Ui.icon(c, "icons/stew.png", fx, hy + 19f * u, 20f)
        Ui.text(c, "${player.stew}", fx + 12f * u, hy + 25f * u, 14f, Color.WHITE, Paint.Align.LEFT)

        if (!inCave) {
            var nearestMound: BuriedMound? = null
            var bestMD = 1e9f
            for (m in mounds) if (!m.isDug) {
                val d = dist(player.cx, player.cy, m.x, m.y)
                if (d < bestMD) { bestMD = d; nearestMound = m }
            }
            if (nearestMound != null) {
                val cy2 = hy + 44f * u
                tmpR.set(14f * u, cy2, 320f * u, cy2 + 30f * u)
                Ui.panel(c, tmpR, Color.parseColor("#FFD54F"), Color.argb(190, 12, 16, 30), 8f)
                Ui.icon(c, "icons/compass.png", 34f * u, cy2 + 15f * u, 20f)
                val meters = (bestMD / OpenWorld.TS).toInt()
                val dir = directionName(nearestMound.x - player.cx, nearestMound.y - player.cy)
                Ui.text(c, "بوصلة الكنز: $meters م ($dir)", 176f * u, cy2 + 20f * u, 13.5f, GOLD)
            }
        }

        val cw = min(460f * u, sw * 0.40f)
        tmpR.set(sw / 2f - cw / 2f, 10f * u, sw / 2f + cw / 2f, 40f * u)
        Ui.panel(c, tmpR, Color.parseColor("#6D4C41"), Color.argb(190, 12, 16, 30), 10f)
        Ui.text(c, "اليوم $dayNo  •  ${timeLabel()}", tmpR.centerX(), tmpR.centerY() + 6f * u, 16f, GOLD)
        tmpR.set(sw / 2f - cw / 2f, 44f * u, sw / 2f + cw / 2f, 54f * u)
        Ui.bar(c, tmpR, dayTime, if (isNight()) Color.parseColor("#5C6BC0") else Color.parseColor("#FFCA28"))

        val inV = !inCave && w.inVillage(player.cx, player.cy)
        val bi = w.biomeAtPx(player.cx, player.cy)
        tmpR.set(sw / 2f - cw / 2f, 60f * u, sw / 2f + cw / 2f, 88f * u)
        Ui.panel(c, tmpR, Color.parseColor("#455A64"), Color.argb(180, 10, 14, 26), 10f)
        val locLabel = when {
            inCave -> "${activeCave?.nameAr ?: "كهف"} (طابق $caveFloor/3)"
            player.inBoat -> "الإبحار بالمركب • ${biomeNames[bi]}"
            inV -> "منطقة قرية آمنة (${biomeNames[bi]})"
            else -> biomeNames[bi]
        }
        Ui.text(c, locLabel, tmpR.centerX(), tmpR.centerY() + 5.5f * u, 15f, if (inV) Color.parseColor("#A5D6A7") else Color.WHITE)

        val mw = 190f * u
        val mx = sw - 14f * u - mw
        val my = 76f * u
        tmpR.set(mx - 4f * u, my - 4f * u, mx + mw + 4f * u, my + mw + 4f * u)
        Ui.panel(c, tmpR, Color.parseColor("#455A64"), Color.argb(215, 6, 8, 16), 8f)
        val mmR = RectF(mx, my, mx + mw, my + mw)
        if (inCave && caveMap != null) {
            c.drawBitmap(caveMap!!.minimap, null, mmR, null)
            val fx2 = mw / caveMap!!.width
            val fy2 = mw / caveMap!!.height
            for (en in enemies) if (!en.isDead) Ui.circle(c, mx + en.cx * fx2, my + en.cy * fy2, 3f * u, Color.RED)
            Ui.circle(c, mx + caveExitSurface.x * fx2, my + caveExitSurface.y * fy2, 4f * u, Color.GREEN)
            caveExitNext?.let { Ui.circle(c, mx + it.x * fx2, my + it.y * fy2, 4.5f * u, Color.MAGENTA) }
            Ui.circle(c, mx + player.cx * fx2, my + player.cy * fy2, 4f * u, Color.WHITE)
        } else {
            c.drawBitmap(w.minimap, null, mmR, null)
            val f = mw / w.width
            for (sv in w.subVillageTiles) Ui.circle(c, mx + sv.first * OpenWorld.TS * f, my + sv.second * OpenWorld.TS * f, 3.5f * u, Color.parseColor("#69F0AE"))
            for (ce in caveEntrances) Ui.circle(c, mx + ce.x * f, my + ce.y * f, 3.2f * u, Color.CYAN)
            for (ft in w.fortressTiles) Ui.circle(c, mx + ft.first * OpenWorld.TS * f, my + ft.second * OpenWorld.TS * f, 3.5f * u, Color.parseColor("#FF5252"))
            for (en in enemies) {
                if (en.isDead) continue
                if (dist(en.cx, en.cy, player.cx, player.cy) > 2000f) continue
                Ui.circle(c, mx + en.cx * f, my + en.cy * f, 2.5f * u, Color.parseColor("#FF8A80"))
            }
            Ui.circle(c, mx + OpenWorld.CX * OpenWorld.TS * f, my + OpenWorld.CY * OpenWorld.TS * f, 4.5f * u, Color.parseColor("#FFD54F"))
            Ui.circle(c, mx + player.cx * f, my + player.cy * f, 4f * u, Color.WHITE)
        }
        drawControls(c)
    }

    private fun directionName(dx: Float, dy: Float): String {
        val a = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        return when {
            a in -22.5f..22.5f -> "شرق"
            a in 22.5f..67.5f -> "جنوب شرق"
            a in 67.5f..112.5f -> "جنوب"
            a in 112.5f..157.5f -> "جنوب غرب"
            a < -157.5f || a > 157.5f -> "غرب"
            a in -157.5f..-112.5f -> "شمال غرب"
            a in -112.5f..-67.5f -> "شمال"
            else -> "شمال شرق"
        }
    }

    // ---- title / end screens
    private val heroSheet = SpriteSheet("sprites/player/idle.png", 0.2f)

    private fun drawTitle(c: Canvas) {
        val u = Ui.u
        val w = curW.toFloat()
        val h = curH.toFloat()
        val camX = (world.width - camera.viewW) / 2f + sin(titleT * 0.12f) * 520f
        val camY = (world.height - camera.viewH) / 2f + sin(titleT * 0.09f + 1f) * 300f
        c.save()
        c.scale(zoom, zoom)
        c.translate(-camX, -camY)
        world.draw(c, camX, camY, camera.viewW, camera.viewH, titleT)
        world.drawDecor(c, camX, camY, camera.viewW, camera.viewH)
        c.restore()
        Ui.fillRect(c, 0f, 0f, w, h, Color.argb(150, 4, 6, 16))
        Ui.text(c, "حرب الفارس", w / 2f, h * 0.22f, 70f, GOLD)
        Ui.text(c, "عابر العصور • إصدار البناء والعالم المفتوح", w / 2f, h * 0.22f + 52f * u, 28f, Color.parseColor("#80DEEA"))
        Ui.text(c, "بناء حر بستايل ماين كرافت، حقيبة شبكية، فرن صهر، تجارة قرويين، وكهوف", w / 2f, h * 0.22f + 88f * u, 16.5f, Color.rgb(220, 224, 235), Paint.Align.CENTER, false)
        heroSheet.update(0.016f)
        heroSheet.draw(c, w / 2f - 75f * u, h * 0.34f, 150f * u, 150f * u)
        val bw = 340f * u
        var y = h * 0.34f + 155f * u
        val ro = RectF(w / 2f - bw / 2f, y, w / 2f + bw / 2f, y + 54f * u)
        Ui.button(c, ro, "العالم المفتوح الضخم (بناء حر)", Color.parseColor("#6A1B9A"), true, 20f)
        hits.add(Hit(ro, "open"))
        y += 62f * u
        if (SaveStore.hasSave()) {
            val r = RectF(w / 2f - bw / 2f, y, w / 2f + bw / 2f, y + 54f * u)
            Ui.button(c, r, "متابعة الرحلة", Color.parseColor("#2E7D32"), true, 22f)
            hits.add(Hit(r, "cont"))
            y += 62f * u
        }
        val r2 = RectF(w / 2f - bw / 2f, y, w / 2f + bw / 2f, y + 54f * u)
        Ui.button(c, r2, if (SaveStore.hasSave()) "طور القصة (رحلة جديدة)" else "ابدأ طور القصة", Color.parseColor("#F9A825"), true, 21f)
        hits.add(Hit(r2, "new"))
        val s1 = RectF(w - 330f * u, h - 64f * u, w - 176f * u, h - 16f * u)
        Ui.button(c, s1, "المؤثرات: " + (if (Sound.sfxOn) "نعم" else "لا"), Color.parseColor("#37474F"), true, 15f)
        hits.add(Hit(s1, "sfx"))
        val s2 = RectF(w - 166f * u, h - 64f * u, w - 16f * u, h - 16f * u)
        Ui.button(c, s2, "الموسيقى: " + (if (Sound.musicOn) "نعم" else "لا"), Color.parseColor("#37474F"), true, 15f)
        hits.add(Hit(s2, "music"))
        val s3 = RectF(16f * u, h - 64f * u, 236f * u, h - 16f * u)
        Ui.button(c, s3, "الجودة: " + (if (lowQuality) "منخفضة" else "عالية"), Color.parseColor("#00838F"), true, 15f)
        hits.add(Hit(s3, "quality"))
    }

    private fun drawGameOver(c: Canvas) {
        val u = Ui.u
        val w = curW.toFloat()
        val h = curH.toFloat()
        Ui.fillRect(c, 0f, 0f, w, h, Color.argb(min(200, (endT * 300f).toInt()), 40, 0, 0))
        val pr = RectF(w / 2f - 260f * u, h / 2f - 170f * u, w / 2f + 260f * u, h / 2f + 170f * u)
        Ui.panel(c, pr, Color.parseColor("#B71C1C"), Color.argb(245, 22, 10, 14), 16f)
        Ui.text(c, "سقط الفارس", pr.centerX(), pr.top + 66f * u, 44f, Color.parseColor("#FF5252"))
        Ui.text(c, "الزمن لم ينتهِ بعد... انهض من جديد", pr.centerX(), pr.top + 104f * u, 18f, Color.rgb(230, 220, 224), Paint.Align.CENTER, false)
        val r1 = RectF(pr.centerX() - 160f * u, pr.top + 132f * u, pr.centerX() + 160f * u, pr.top + 192f * u)
        Ui.button(c, r1, "حاول مرة أخرى", Color.parseColor("#2E7D32"), endT > 0.8f, 21f)
        hits.add(Hit(r1, "retry"))
        val r2 = RectF(pr.centerX() - 160f * u, pr.top + 208f * u, pr.centerX() + 160f * u, pr.top + 268f * u)
        Ui.button(c, r2, "القائمة الرئيسية", Color.parseColor("#455A64"), endT > 0.8f, 21f)
        hits.add(Hit(r2, "title"))
    }

    private fun drawVictory(c: Canvas) {
        val u = Ui.u
        val w = curW.toFloat()
        val h = curH.toFloat()
        Ui.fillRect(c, 0f, 0f, w, h, Color.rgb(10, 8, 24))
        for (i in 0 until 60) {
            val sx = ((i * 97) % 100) / 100f * w
            val sy = (((i * 53) % 100) / 100f * h + endT * (10f + (i % 5) * 6f)) % h
            Ui.circle(c, sx, sy, (1.5f + (i % 3)) * u, Color.argb(200, 255, 240, 180))
        }
        Ui.text(c, "تحرر الزمن", w / 2f, h * 0.25f, 66f, GOLD)
        Ui.text(c, "جمعتَ الشظايا الست وأصلحتَ حجر كرونو", w / 2f, h * 0.25f + 50f * u, 24f, Color.parseColor("#80DEEA"))
        Ui.wrapped(
            c, "لم يكن العدو في أي عصر سوى صدى لقرارٍ قديم. قبلتَ ما حدث فاستأنف الزمن مساره... لكن الليل صار أطول، وشيءٌ ما في الظلام استيقظ.",
            w / 2f - 360f * u, h * 0.25f + 78f * u, 720f * u, 21f, Color.rgb(230, 232, 240), false, 3, true, false
        )
        Ui.icon(c, "icons/coin.png", w / 2f - 70f * u, h * 0.25f + 200f * u, 32f)
        Ui.text(c, "${player.coins}", w / 2f - 46f * u, h * 0.25f + 210f * u, 28f, GOLD, Paint.Align.LEFT)
        val ro = RectF(w / 2f - 190f * u, h * 0.25f + 244f * u, w / 2f + 190f * u, h * 0.25f + 304f * u)
        Ui.button(c, ro, "ابدأ العالم المفتوح", Color.parseColor("#6A1B9A"), endT > 0.8f, 22f)
        hits.add(Hit(ro, "open"))
        val r = RectF(w / 2f - 190f * u, h * 0.25f + 316f * u, w / 2f + 190f * u, h * 0.25f + 372f * u)
        Ui.button(c, r, "القائمة الرئيسية", Color.parseColor("#455A64"), endT > 0.8f, 20f)
        hits.add(Hit(r, "title"))
    }
}
