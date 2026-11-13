package com.clashangprpr.app.model

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.Paint
import android.graphics.RectF
import com.clashangprpr.app.engine.Assets
import com.clashangprpr.app.engine.SaveStore
import com.clashangprpr.app.engine.SpriteSheet
import com.clashangprpr.app.world.Body
import com.clashangprpr.app.world.Collider
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private object Fx {
    val white = Paint().apply { colorFilter = LightingColorFilter(0x000000, 0xFFFFFF) }
    val red = Paint().apply { colorFilter = LightingColorFilter(0xFFFFFF, 0x703030) }
    val blue = Paint().apply { colorFilter = LightingColorFilter(0xB0C8FF, 0x101840) }
    val gold = Paint().apply { colorFilter = LightingColorFilter(0xFFE082, 0x302000) }
    val ghost = Paint().apply { alpha = 120 }
    val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(85, 0, 0, 0) }
    val slash = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 8f; strokeCap = Paint.Cap.ROUND }
    val bar = Paint(Paint.ANTI_ALIAS_FLAG)
    val oval = RectF()
}

class Player(override var x: Float, override var y: Float) : Body {
    companion object {
        /** قدرة الدفع (الاندفاع السريع) تتجدد كل 6 ثوانٍ حسب طلب اللاعب */
        const val DASH_CD = 6.0f
        const val SLOW_CD = 9f
        const val SLOW_DUR = 3.5f
        const val MAGIC_CD = 0.45f
        const val BOMB_CD = 0.6f
        const val MAGIC_COST = 22f
        const val SLOW_COST = 30f
    }

    val width = 96f
    val height = 96f
    override val fOffX = 33f
    override val fOffY = 66f
    override val fW = 30f
    override val fH = 22f

    var hp = 180
    var maxHp = 180
    var mana = 100f
    var maxMana = 100
    var coins = 80
    var wood = 10
    var stone = 8
    var iron = 4
    var wool = 3
    var bombs = 2
    var potions = 2
    var speedPots = 1
    var stew = 2
    var goldKeys = 1
    var treasureMaps = 1
    var bedsItem = 1
    var furnacesItem = 1
    var cannonsItem = 0
    var campfiresItem = 1

    // Minecraft-style building block counts (start with starter blocks so player can build right away!)
    var blkWood = 16
    var blkStone = 12
    var blkBridge = 12
    var blkDoor = 3
    var blkFence = 8
    var blkTorch = 8
    var blkGlass = 6
    var blkTnt = 2

    /** Selected slot index (0..8) in the bottom Minecraft Hotbar */
    var hotbarIdx = 0

    val mats = IntArray(6)
    var weaponLvl = 0
    var armorLvl = 0
    var apples = 3
    var rawMeat = 2
    var cookedMeat = 2
    var hasBossKey = false
    var hasPickaxe = true
    var hasOceanGem = false
    var hasPlasma = false
    var hasBoat = false
    var inBoat = false
    var spellIdx = 0
    val owned = HashSet<String>()
    var weapon: Weapon = GameCatalogue.WEAPON_BASIC
    var armorId = ""
    var upgHp = 0
    var upgMana = 0

    var aimX = 1f
    var aimY = 0f
    var facingLeft = false
    var moving = false
    var isDead = false
    var isAttacking = false
    var swingTime = 0f
    var attackCd = 0f
    val swingHit = HashSet<Any>()
    var isDashing = false
    var dashTimer = 0f
    var dashSpeedAfter = 0f
    var dashCd = 0f
    var slowTimer = 0f
    var slowCd = 0f
    var magicCd = 0f
    var bombCd = 0f
    var invincible = 0f
    var hurtTimer = 0f
    var speedBuffTimer = 0f
    var visionBuffTimer = 0f
    var shieldBuffTimer = 0f

    private val idleA = SpriteSheet("sprites/player/idle.png", 0.2f)
    private val walkA = SpriteSheet("sprites/player/walk.png", 0.11f)
    private val attackA = SpriteSheet("sprites/player/attack.png", 0.07f, false)
    private val deadA = SpriteSheet("sprites/player/dead.png", 0.25f, false)

    val cx: Float get() = x + width / 2f
    val cy: Float get() = y + 60f
    val hitbox = RectF()
    val currentSpell: SpellType get() = SpellType.values()[spellIdx.coerceIn(0, SpellType.values().size - 1)]

    init {
        owned.add("sword_basic")
        recomputeStats()
        hp = maxHp
    }

    fun blockCountFor(b: BlockType): Int = when (b) {
        BlockType.WOOD_WALL -> blkWood
        BlockType.STONE_BRICK -> blkStone
        BlockType.WOOD_BRIDGE -> blkBridge
        BlockType.DOOR_CLOSED -> blkDoor
        BlockType.FENCE -> blkFence
        BlockType.TORCH -> blkTorch
        BlockType.GLASS_BLOCK -> blkGlass
        BlockType.TNT_BLOCK -> blkTnt
    }

    fun addBlockCount(b: BlockType, delta: Int) {
        when (b) {
            BlockType.WOOD_WALL -> blkWood = max(0, blkWood + delta)
            BlockType.STONE_BRICK -> blkStone = max(0, blkStone + delta)
            BlockType.WOOD_BRIDGE -> blkBridge = max(0, blkBridge + delta)
            BlockType.DOOR_CLOSED -> blkDoor = max(0, blkDoor + delta)
            BlockType.FENCE -> blkFence = max(0, blkFence + delta)
            BlockType.TORCH -> blkTorch = max(0, blkTorch + delta)
            BlockType.GLASS_BLOCK -> blkGlass = max(0, blkGlass + delta)
            BlockType.TNT_BLOCK -> blkTnt = max(0, blkTnt + delta)
        }
    }

    fun selectedBlockType(): BlockType? = when (hotbarIdx) {
        2 -> BlockType.WOOD_WALL
        3 -> BlockType.STONE_BRICK
        4 -> BlockType.WOOD_BRIDGE
        5 -> BlockType.DOOR_CLOSED
        6 -> BlockType.TORCH
        7 -> BlockType.FENCE
        8 -> BlockType.TNT_BLOCK
        else -> null
    }

    fun armorReduction(): Float {
        val base = (GameCatalogue.armorById(armorId)?.reduction ?: 0f) + armorLvl * 0.02f + (if (shieldBuffTimer > 0f) 0.25f else 0f)
        return min(0.75f, base)
    }

    fun recomputeStats() {
        val bonus = GameCatalogue.armorById(armorId)?.hpBonus ?: 0
        maxHp = 180 + bonus + upgHp * 30 + armorLvl * 25
        maxMana = 100 + upgMana * 25
        hp = min(hp, maxHp)
        mana = min(mana, maxMana.toFloat())
    }

    fun equipBest() {
        var best = GameCatalogue.WEAPON_BASIC
        for (w in GameCatalogue.weapons) if (owned.contains(w.id) && w.damage > best.damage) best = w
        weapon = best
    }

    fun resetState() {
        isDead = false
        isAttacking = false
        isDashing = false
        slowTimer = 0f
        slowCd = 0f
        dashCd = 0f
        dashSpeedAfter = 0f
        attackCd = 0f
        magicCd = 0f
        bombCd = 0f
        hurtTimer = 0f
        invincible = 1.5f
        hasBossKey = false
        hasOceanGem = false
        refillAll()
    }

    fun refillAll() {
        hp = maxHp
        mana = maxMana.toFloat()
    }

    val hasGodblade: Boolean get() = owned.contains("godblade")

    fun update(dt: Float, mx: Float, my: Float, world: Collider) {
        if (isDead) {
            deadA.update(dt)
            return
        }
        if (attackCd > 0f) attackCd -= dt
        if (dashCd > 0f) dashCd -= dt
        if (dashSpeedAfter > 0f) dashSpeedAfter -= dt
        if (slowCd > 0f) slowCd -= dt
        if (magicCd > 0f) magicCd -= dt
        if (bombCd > 0f) bombCd -= dt
        if (invincible > 0f) invincible -= dt
        if (hurtTimer > 0f) hurtTimer -= dt
        if (slowTimer > 0f) slowTimer -= dt
        if (speedBuffTimer > 0f) speedBuffTimer -= dt
        if (visionBuffTimer > 0f) visionBuffTimer -= dt
        if (shieldBuffTimer > 0f) shieldBuffTimer -= dt
        mana = min(maxMana.toFloat(), mana + 15f * dt)

        val len = sqrt(mx * mx + my * my)
        moving = len > 0.1f
        if (moving) {
            aimX = mx / len
            aimY = my / len
            if (abs(mx) > 0.2f) facingLeft = mx < 0f
        }
        if (isDashing) {
            dashTimer -= dt
            world.move(this, aimX * 720f * dt, aimY * 720f * dt)
            if (dashTimer <= 0f) {
                isDashing = false
                dashSpeedAfter = 1.6f
            }
        } else if (moving) {
            var spd = 255f * (if (isAttacking) 0.65f else 1f)
            if (dashSpeedAfter > 0f) spd *= 1.38f
            if (speedBuffTimer > 0f) spd *= 1.35f
            if (inBoat) spd *= 1.32f
            world.move(this, mx * spd * dt, my * spd * dt)
        }
        if (isAttacking) {
            swingTime += dt
            attackA.update(dt)
            if (swingTime >= 0.30f) isAttacking = false
        }
        idleA.update(dt)
        walkA.update(dt)
        hitbox.set(x + 28f, y + 26f, x + 68f, y + 88f)
    }

    val inHitWindow: Boolean get() = isAttacking && swingTime in 0.06f..0.22f

    fun attackRect(out: RectF) {
        val r = weapon.reach
        val ax = cx + aimX * r * 0.60f
        val ay = cy + aimY * r * 0.60f
        val half = r * 0.62f
        out.set(ax - half, ay - half, ax + half, ay + half)
    }

    fun attack(): Boolean {
        if (isDead || isAttacking || attackCd > 0f || isDashing) return false
        isAttacking = true
        swingTime = 0f
        attackCd = weapon.cooldown
        swingHit.clear()
        attackA.reset()
        return true
    }

    fun dash(): Boolean {
        if (isDead || isDashing || dashCd > 0f) return false
        isDashing = true
        dashTimer = 0.26f
        dashCd = DASH_CD
        invincible = max(invincible, 0.40f)
        return true
    }

    fun slowmo(): Boolean {
        if (isDead || slowCd > 0f || slowTimer > 0f || mana < SLOW_COST) return false
        mana -= SLOW_COST
        slowTimer = SLOW_DUR
        slowCd = SLOW_CD
        return true
    }

    fun nextSpell() {
        spellIdx = (spellIdx + 1) % SpellType.values().size
    }

    fun castSpell(projList: MutableList<Projectile>): Boolean {
        val sp = currentSpell
        if (isDead || magicCd > 0f || mana < sp.manaCost) return false
        mana -= sp.manaCost
        magicCd = sp.cooldown
        val god = hasGodblade
        val baseDmg = (if (god) 165 else if (hasPlasma) 125 else 80) + weaponLvl * 10
        when (sp) {
            SpellType.ARCANE_BOLT -> {
                val kind = if (god || hasPlasma) Projectile.LASER else Projectile.MAGIC
                projList.add(Projectile(cx + aimX * 30f, cy + aimY * 30f, aimX * 650f, aimY * 650f, baseDmg, kind, true))
            }
            SpellType.FIRE_NOVA -> {
                for (i in 0 until 8) {
                    val a = i * (Math.PI.toFloat() * 2f / 8f)
                    projList.add(Projectile(cx, cy, cos(a) * 480f, sin(a) * 480f, (baseDmg * 0.85f).toInt(), Projectile.FIREBALL, true))
                }
            }
            SpellType.FROST_HEAL -> {
                hp = min(maxHp, hp + 45)
                shieldBuffTimer = 8f
                for (i in 0 until 6) {
                    val a = i * (Math.PI.toFloat() * 2f / 6f)
                    projList.add(Projectile(cx, cy, cos(a) * 420f, sin(a) * 420f, (baseDmg * 0.6f).toInt(), Projectile.MAGIC, true))
                }
            }
            SpellType.VOID_LASER -> {
                projList.add(Projectile(cx + aimX * 30f, cy + aimY * 30f, aimX * 760f, aimY * 760f, (baseDmg * 1.35f).toInt(), Projectile.LASER, true))
            }
        }
        return true
    }

    fun throwBomb(): Projectile? {
        if (isDead || bombs <= 0 || bombCd > 0f) return null
        bombs--
        bombCd = BOMB_CD
        return Projectile(cx + aimX * 30f, cy + aimY * 30f, aimX * 430f, aimY * 430f, 120, Projectile.BOMB, true)
    }

    fun usePotion(): Boolean {
        if (isDead || potions <= 0 || hp >= maxHp) return false
        potions--
        hp = min(maxHp, hp + 85)
        return true
    }

    fun useSpeedPotion(): Boolean {
        if (isDead || speedPots <= 0) return false
        speedPots--
        speedBuffTimer = 45f
        visionBuffTimer = 60f
        return true
    }

    fun takeDamage(amount: Int): Boolean {
        if (isDead || invincible > 0f) return false
        val dmg = max(1, (amount * (1f - armorReduction())).toInt())
        hp -= dmg
        invincible = 0.5f
        hurtTimer = 0.22f
        if (hp <= 0) {
            hp = 0
            isDead = true
            deadA.reset()
        }
        return true
    }

    fun cooldownFrac(cd: Float, total: Float): Float = if (cd > 0f) (cd / total).coerceIn(0f, 1f) else 0f

    fun draw(c: Canvas, time: Float) {
        if (inBoat) {
            Fx.bar.style = Paint.Style.FILL
            Fx.bar.color = Color.argb(120, 0, 188, 212)
            Fx.oval.set(x + 8f, y + 66f, x + 88f, y + 98f)
            c.drawOval(Fx.oval, Fx.bar)
            Fx.bar.color = Color.rgb(121, 85, 72)
            Fx.oval.set(x + 12f, y + 62f, x + 84f, y + 94f)
            c.drawRoundRect(Fx.oval, 16f, 16f, Fx.bar)
            Fx.bar.color = Color.rgb(161, 136, 127)
            Fx.oval.set(x + 18f, y + 66f, x + 78f, y + 88f)
            c.drawRoundRect(Fx.oval, 12f, 12f, Fx.bar)
        } else {
            Fx.oval.set(x + 26f, y + 76f, x + 70f, y + 92f)
            c.drawOval(Fx.oval, Fx.shadow)
        }
        var paint: Paint? = null
        if (hurtTimer > 0f) paint = Fx.white
        else if (isDashing) paint = Fx.ghost
        else if (slowTimer > 0f) paint = Fx.blue
        else if (speedBuffTimer > 0f && ((time * 10f).toInt() % 2 == 0)) paint = Fx.gold
        else if (invincible > 0f && ((time * 18f).toInt() % 2 == 0)) paint = Fx.ghost
        val sheet = when {
            isDead -> deadA
            isAttacking -> attackA
            moving || isDashing -> walkA
            else -> idleA
        }
        sheet.draw(c, x, y, width, height, facingLeft, paint)
        if (shieldBuffTimer > 0f && !isDead) {
            Fx.slash.color = Color.parseColor("#69F0AE")
            Fx.slash.alpha = (140 + 70 * sin(time * 6f)).toInt().coerceIn(0, 255)
            Fx.slash.strokeWidth = 4f
            c.drawCircle(cx, cy - 6f, 44f, Fx.slash)
            Fx.slash.strokeWidth = 8f
        }
        if (isAttacking && !isDead) {
            val p = (swingTime / 0.30f).coerceIn(0f, 1f)
            val ang = Math.toDegrees(atan2(aimY.toDouble(), aimX.toDouble())).toFloat()
            val rad = weapon.reach * 0.82f
            Fx.oval.set(cx - rad, cy - rad, cx + rad, cy + rad)
            Fx.slash.color = weapon.color
            Fx.slash.alpha = (230 * (1f - p)).toInt().coerceIn(0, 255)
            c.drawArc(Fx.oval, ang - 70f, 140f * min(1f, p * 2.2f), false, Fx.slash)
        }
    }

    // -------------------------------------------------------- persistence
    fun save(eraIndex: Int) {
        val m = HashMap<String, Any>()
        m["era"] = eraIndex
        m["mode"] = "era"
        m["coins"] = coins
        m["wood"] = wood
        m["stone"] = stone
        m["iron"] = iron
        m["wool"] = wool
        m["bombs"] = bombs
        m["potions"] = potions
        m["speedPots"] = speedPots
        m["stew"] = stew
        m["goldKeys"] = goldKeys
        m["tMaps"] = treasureMaps
        m["bedsI"] = bedsItem
        m["furnI"] = furnacesItem
        m["canI"] = cannonsItem
        m["campI"] = campfiresItem
        m["bWood"] = blkWood
        m["bStone"] = blkStone
        m["bBridge"] = blkBridge
        m["bDoor"] = blkDoor
        m["bFence"] = blkFence
        m["bTorch"] = blkTorch
        m["bGlass"] = blkGlass
        m["bTnt"] = blkTnt
        m["boat"] = hasBoat
        m["spell"] = spellIdx
        m["owned"] = owned.joinToString(",")
        m["armor"] = armorId
        m["upgHp"] = upgHp
        m["upgMana"] = upgMana
        m["mats"] = mats.joinToString(",")
        m["wLvl"] = weaponLvl
        m["apples"] = apples
        m["rawm"] = rawMeat
        m["cookm"] = cookedMeat
        m["aLvl"] = armorLvl
        m["pickaxe"] = hasPickaxe
        m["plasma"] = hasPlasma
        SaveStore.putAll(m)
    }

    /** Loads inventory from the last checkpoint and returns the era index. */
    fun load(): Int {
        coins = SaveStore.int("coins", 80)
        wood = SaveStore.int("wood", 10)
        stone = SaveStore.int("stone", 8)
        iron = SaveStore.int("iron", 4)
        wool = SaveStore.int("wool", 3)
        bombs = SaveStore.int("bombs", 2)
        potions = SaveStore.int("potions", 2)
        speedPots = SaveStore.int("speedPots", 1)
        stew = SaveStore.int("stew", 2)
        goldKeys = SaveStore.int("goldKeys", 1)
        treasureMaps = SaveStore.int("tMaps", 1)
        bedsItem = SaveStore.int("bedsI", 1)
        furnacesItem = SaveStore.int("furnI", 1)
        cannonsItem = SaveStore.int("canI", 0)
        campfiresItem = SaveStore.int("campI", 1)
        blkWood = SaveStore.int("bWood", 16)
        blkStone = SaveStore.int("bStone", 12)
        blkBridge = SaveStore.int("bBridge", 12)
        blkDoor = SaveStore.int("bDoor", 3)
        blkFence = SaveStore.int("bFence", 8)
        blkTorch = SaveStore.int("bTorch", 8)
        blkGlass = SaveStore.int("bGlass", 6)
        blkTnt = SaveStore.int("bTnt", 2)
        hasBoat = SaveStore.bool("boat", false)
        spellIdx = SaveStore.int("spell", 0)
        owned.clear()
        owned.add("sword_basic")
        for (s in SaveStore.str("owned", "sword_basic").split(",")) if (s.isNotEmpty()) owned.add(s)
        armorId = SaveStore.str("armor", "")
        upgHp = SaveStore.int("upgHp", 0)
        upgMana = SaveStore.int("upgMana", 0)
        val ms = SaveStore.str("mats", "0,0,0,0,0,0").split(",")
        for (i in 0 until 6) mats[i] = if (i < ms.size) (ms[i].toIntOrNull() ?: 0) else 0
        weaponLvl = SaveStore.int("wLvl", 0)
        apples = SaveStore.int("apples", 3)
        rawMeat = SaveStore.int("rawm", 2)
        cookedMeat = SaveStore.int("cookm", 2)
        armorLvl = SaveStore.int("aLvl", 0)
        hasPickaxe = SaveStore.bool("pickaxe", true)
        hasPlasma = SaveStore.bool("plasma", false)
        hasBossKey = false
        hasOceanGem = false
        equipBest()
        recomputeStats()
        refillAll()
        isDead = false
        return SaveStore.int("era", 0)
    }
}

// ------------------------------------------------------------------ Sheep (الخراف)

class Sheep(override var x: Float, override var y: Float) : Body {
    override val fOffX = 12f
    override val fOffY = 28f
    override val fW = 36f
    override val fH = 22f

    var hp = 3
    var isDead = false
    var woolReady = true
    private var regrowTimer = 0f
    private var moveTimer = 2f
    private var vx = 0f
    private var vy = 0f
    private var fleeTimer = 0f
    private var flash = 0f
    private var bob = (x * 0.02f)
    val hitbox = RectF()
    val cx: Float get() = x + 30f
    val cy: Float get() = y + 30f

    fun hit(fromX: Float, fromY: Float): Boolean {
        if (isDead) return false
        hp--
        flash = 0.16f
        fleeTimer = 3.5f
        val dx = cx - fromX
        val dy = cy - fromY
        val l = max(1f, sqrt(dx * dx + dy * dy))
        vx = dx / l * 190f
        vy = dy / l * 190f
        if (hp <= 0) isDead = true
        return true
    }

    fun update(dt: Float, world: Collider) {
        if (isDead) return
        bob += dt * 6f
        if (flash > 0f) flash -= dt
        if (!woolReady) {
            regrowTimer += dt
            if (regrowTimer > 45f) {
                regrowTimer = 0f
                woolReady = true
            }
        }
        if (fleeTimer > 0f) {
            fleeTimer -= dt
            world.move(this, vx * dt, vy * dt)
        } else {
            moveTimer -= dt
            if (moveTimer <= 0f) {
                moveTimer = 1.8f + (abs(x.toInt()) % 3)
                if ((bob.toInt() % 3) == 0) {
                    vx = 0f
                    vy = 0f
                } else {
                    val a = (bob * 3.7f) % 6.283f
                    vx = cos(a) * 58f
                    vy = sin(a) * 58f
                }
            }
            if (vx != 0f || vy != 0f) world.move(this, vx * dt, vy * dt)
        }
        hitbox.set(x + 6f, y + 6f, x + 54f, y + 54f)
    }

    fun draw(c: Canvas) {
        if (isDead) return
        val b = if (vx != 0f || vy != 0f) sin(bob) * 2.5f else 0f
        Fx.oval.set(x + 10f, y + 42f, x + 50f, y + 54f)
        c.drawOval(Fx.oval, Fx.shadow)
        val p = Fx.bar
        p.style = Paint.Style.FILL
        p.color = Color.rgb(62, 39, 35)
        c.drawRect(x + 16f, y + 36f + b, x + 21f, y + 48f, p)
        c.drawRect(x + 38f, y + 36f - b, x + 43f, y + 48f, p)
        p.color = if (flash > 0f) Color.rgb(255, 180, 180) else if (woolReady) Color.rgb(245, 245, 250) else Color.rgb(215, 204, 200)
        Fx.oval.set(x + 10f, y + 14f + b, x + 48f, y + 42f + b)
        c.drawOval(Fx.oval, p)
        if (woolReady) {
            c.drawCircle(x + 22f, y + 18f + b, 11f, p)
            c.drawCircle(x + 34f, y + 18f + b, 11f, p)
            c.drawCircle(x + 28f, y + 26f + b, 12f, p)
        }
        val headX = if (vx < -2f) x + 10f else x + 46f
        p.color = Color.rgb(78, 52, 46)
        c.drawCircle(headX, y + 24f + b, 9f, p)
        p.color = Color.WHITE
        c.drawCircle(headX + (if (vx < -2f) -3f else 3f), y + 22f + b, 2.2f, p)
        WText.draw(c, "خروف", x + 30f, y + 4f, 15f, Color.parseColor("#FFF59D"))
    }
}

// ------------------------------------------------------------------ Villagers (قرويون للتبادل والتجارة والسرقة بستايل ماين كرافت)

enum class VillagerRole(val titleAr: String, val color: Int) {
    TRADER("تاجر القرية", Color.parseColor("#81C784")),
    BLACKSMITH("حداد الدروع والأسلحة", Color.parseColor("#FFB74D")),
    ELDER("حكيم القرية والمهندس", Color.parseColor("#64B5F6")),
    FARMER("مزارع وطاهٍ", Color.parseColor("#AED581")),
    PIRATE_SMUGGLER("تاجر الميناء والبحار", Color.parseColor("#CE93D8"))
}

class VillagerNPC(
    override var x: Float,
    override var y: Float,
    val nameAr: String,
    val role: VillagerRole,
    val tradeGiveText: String,
    val tradeGetText: String,
    val tradeId: String
) : Body {
    override val fOffX = 28f
    override val fOffY = 60f
    override val fW = 28f
    override val fH = 20f

    val homeX = x
    val homeY = y
    val size = 88f
    private val sheet: SpriteSheet
    private val tintP: Paint?
    private var walkTimer = 2f
    private var vx = 0f
    private var vy = 0f
    var stealCd = 0f
    var level = 1
    var xp = 0

    init {
        val path = when (role) {
            VillagerRole.BLACKSMITH -> "sprites/kit8/idle.png"
            VillagerRole.ELDER -> "sprites/priest1/idle.png"
            VillagerRole.PIRATE_SMUGGLER -> "sprites/priest2/idle.png"
            VillagerRole.TRADER -> "sprites/priest3/idle.png"
            else -> "sprites/kit3/idle.png"
        }
        sheet = SpriteSheet(Assets.firstExisting(path, "sprites/merchant/idle.png") ?: "sprites/merchant/idle.png", 0.2f)
        tintP = when (role) {
            VillagerRole.FARMER -> Paint().apply { colorFilter = LightingColorFilter(0xFFEEAA, 0) }
            else -> null
        }
    }

    fun addTradeXp() {
        xp++
        if (xp >= level * 2 && level < 5) {
            level++
            xp = 0
        }
    }

    fun update(dt: Float, world: Collider) {
        sheet.update(dt)
        if (stealCd > 0f) stealCd -= dt
        walkTimer -= dt
        if (walkTimer <= 0f) {
            walkTimer = 2.4f + (abs(x.toInt()) % 3)
            val dx = homeX - x
            val dy = homeY - y
            if (dx * dx + dy * dy > 160f * 160f) {
                val l = max(1f, sqrt(dx * dx + dy * dy))
                vx = dx / l * 48f
                vy = dy / l * 48f
            } else if ((walkTimer.toInt() % 2) == 0) {
                vx = 0f
                vy = 0f
            } else {
                val a = (x * 0.1f + walkTimer) * 2.5f
                vx = cos(a) * 42f
                vy = sin(a) * 42f
            }
        }
        if (vx != 0f || vy != 0f) world.move(this, vx * dt, vy * dt)
    }

    fun isNear(px: Float, py: Float): Boolean {
        val dx = px - (x + size / 2f)
        val dy = py - (y + size * 0.6f)
        return sqrt(dx * dx + dy * dy) < 120f
    }

    fun draw(c: Canvas) {
        Fx.oval.set(x + 20f, y + 68f, x + 68f, y + 84f)
        c.drawOval(Fx.oval, Fx.shadow)
        sheet.draw(c, x, y, size, size, vx < 0f, tintP)
        WText.draw(c, "$nameAr (مستوى $level)", x + size / 2f, y - 18f, 17f, role.color)
        WText.draw(c, tradeGiveText, x + size / 2f, y - 2f, 13.5f, Color.WHITE)
    }
}

enum class EnemyRole { MELEE, SPEARMAN, ARCHER, HEAVY, GHOUL, TRIBAL, COMMANDO, CLONE, BOSS }

class Enemy(
    override var x: Float,
    override var y: Float,
    val role: EnemyRole,
    val era: EraType,
    val holdsKey: Boolean = false
) : Body {
    val isBoss = role == EnemyRole.BOSS
    private val eraMul = 1f + 0.12f * era.ordinal

    val size: Float = when (role) {
        EnemyRole.BOSS -> 128f + 8f * era.ordinal
        EnemyRole.HEAVY -> 100f
        EnemyRole.CLONE -> 92f
        EnemyRole.COMMANDO -> 90f
        else -> 84f
    }
    val maxHp: Int = (when (role) {
        EnemyRole.MELEE -> 85
        EnemyRole.SPEARMAN -> 90
        EnemyRole.ARCHER -> 60
        EnemyRole.HEAVY -> 220
        EnemyRole.GHOUL -> 70
        EnemyRole.TRIBAL -> 95
        EnemyRole.COMMANDO -> 85
        EnemyRole.CLONE -> 110
        EnemyRole.BOSS -> 650 + 160 * era.ordinal
    } * (if (isBoss) 1f else eraMul)).toInt()
    var hp = maxHp
    val damage: Int = (when (role) {
        EnemyRole.MELEE -> 16
        EnemyRole.SPEARMAN -> 15
        EnemyRole.ARCHER -> 12
        EnemyRole.HEAVY -> 24
        EnemyRole.GHOUL -> 14
        EnemyRole.TRIBAL -> 17
        EnemyRole.COMMANDO -> 13
        EnemyRole.CLONE -> 20
        EnemyRole.BOSS -> 26 + 3 * era.ordinal
    } * (if (isBoss) 1f else (1f + 0.06f * era.ordinal))).toInt()
    val speed: Float = when (role) {
        EnemyRole.MELEE -> 108f
        EnemyRole.SPEARMAN -> 100f
        EnemyRole.ARCHER -> 90f
        EnemyRole.HEAVY -> 74f
        EnemyRole.GHOUL -> 150f
        EnemyRole.TRIBAL -> 118f
        EnemyRole.COMMANDO -> 92f
        EnemyRole.CLONE -> 132f
        EnemyRole.BOSS -> 98f
    }
    val reach: Float = when (role) {
        EnemyRole.SPEARMAN -> 104f
        EnemyRole.BOSS -> 112f
        EnemyRole.HEAVY -> 86f
        else -> 74f
    }
    val ranged = role == EnemyRole.ARCHER || role == EnemyRole.COMMANDO
    val reward: Int = if (isBoss) 140 else (10 + 2 * era.ordinal + (if (role == EnemyRole.HEAVY) 8 else 0))

    override val fOffX = size * 0.34f
    override val fOffY = size * 0.68f
    override val fW = size * 0.32f
    override val fH = size * 0.22f

    val homeX = x
    val homeY = y
    var aggro = false
    var isDead = false
    var deadTimer = 0f
    var facingLeft = true
    var moving = false
    var hurtTimer = 0f
    var windup = 0f
    private var cooldown = 0.4f
    private var shootTimer = 1.5f
    private var volleyTimer = 3f
    var summonTimer = 9f
    var wantSummon = false
    private var kx = 0f
    private var ky = 0f
    private var stuck = 0f
    private var sideSign = 1f
    var shielded = false
    var lastKnownHp = hp

    val phase2: Boolean get() = isBoss && hp < maxHp / 2

    val cx: Float get() = x + size / 2f
    val cy: Float get() = y + size * 0.7f
    val hitbox = RectF()

    private val folder: String
    private val tintPaint: Paint?
    private val idleA: SpriteSheet
    private val walkA: SpriteSheet
    private val attackA: SpriteSheet
    private val deadA: SpriteSheet

    init {
        var f = "kit7"
        var tint = 0
        when (role) {
            EnemyRole.BOSS -> when (era) {
                EraType.ERA_1_MEDIEVAL -> f = "kit2"
                EraType.ERA_2_CAVES -> { f = "kit1"; tint = 0xFFC09070.toInt() }
                EraType.ERA_3_ZOMBIE -> f = "zombie_titan"
                EraType.ERA_4_ISLAND -> f = "pirate_captain"
                EraType.ERA_5_CYBER -> f = "cyber_boss"
                EraType.ERA_6_VOID -> { f = "kit2"; tint = 0xFFD070FF.toInt() }
            }
            EnemyRole.MELEE -> f = when (era) {
                EraType.ERA_3_ZOMBIE -> "zombie"
                EraType.ERA_4_ISLAND -> "tribal_warrior"
                else -> "kit7"
            }
            EnemyRole.SPEARMAN -> {
                f = if (era == EraType.ERA_4_ISLAND) "island_spearman" else "kit5"
                if (era == EraType.ERA_2_CAVES) tint = 0xFF9AAAAA.toInt()
            }
            EnemyRole.ARCHER -> f = "archer"
            EnemyRole.HEAVY -> {
                f = when (era) {
                    EraType.ERA_5_CYBER -> "cyber_heavy"
                    EraType.ERA_1_MEDIEVAL -> "kit8"
                    else -> "kit9"
                }
                if (era == EraType.ERA_2_CAVES) tint = 0xFFAA9988.toInt()
            }
            EnemyRole.GHOUL -> {
                f = "zombie_ghoul"
                if (era == EraType.ERA_2_CAVES) tint = 0xFF99BBAA.toInt()
            }
            EnemyRole.TRIBAL -> f = "tribal_warrior"
            EnemyRole.COMMANDO -> {
                f = "cyber_commando"
                if (era == EraType.ERA_6_VOID) tint = 0xFFD090FF.toInt()
            }
            EnemyRole.CLONE -> { f = "kit2"; tint = 0xFFC070FF.toInt() }
        }
        folder = f
        tintPaint = if (tint != 0) Paint().apply { colorFilter = LightingColorFilter(tint, 0) } else null
        idleA = SpriteSheet(Assets.firstExisting("sprites/$f/idle.png") ?: "sprites/$f/idle.png", 0.2f)
        walkA = SpriteSheet(Assets.firstExisting("sprites/$f/walk.png", "sprites/$f/walk+shot.png", "sprites/$f/idle.png") ?: "sprites/$f/walk.png", 0.12f)
        attackA = SpriteSheet(
            Assets.firstExisting("sprites/$f/attack.png", "sprites/$f/attack_1.png", "sprites/$f/shot.png", "sprites/$f/walk.png") ?: "sprites/$f/attack.png",
            0.09f, false
        )
        deadA = SpriteSheet(Assets.firstExisting("sprites/$f/dead.png", "sprites/$f/idle.png") ?: "sprites/$f/dead.png", 0.2f, false)
    }

    fun takeHit(dmg: Int, fromX: Float, fromY: Float, knock: Float = 1f): Int {
        if (isDead) return 0
        aggro = true
        if (shielded) {
            hurtTimer = 0.05f
            return 0
        }
        hp -= dmg
        hurtTimer = 0.14f
        if (!isBoss) {
            val dx = cx - fromX
            val dy = cy - fromY
            val l = max(1f, sqrt(dx * dx + dy * dy))
            kx = dx / l * 300f * knock
            ky = dy / l * 300f * knock
        }
        if (hp <= 0) {
            hp = 0
            isDead = true
            deadTimer = 0f
            deadA.reset()
        }
        return dmg
    }

    private fun stepTo(dxn: Float, dyn: Float, mul: Float, dt: Float, world: Collider) {
        val len = sqrt(dxn * dxn + dyn * dyn)
        if (len < 0.001f) return
        val nx = dxn / len
        val ny = dyn / len
        val spd = speed * mul * (if (phase2) 1.25f else 1f)
        val hit = world.move(this, nx * spd * dt, ny * spd * dt)
        if (hit) {
            stuck += dt
            world.move(this, -ny * sideSign * spd * dt, nx * sideSign * spd * dt)
            if (stuck > 0.5f) {
                sideSign = -sideSign
                stuck = 0f
            }
        } else {
            stuck = max(0f, stuck - dt)
        }
        moving = true
        if (abs(nx) > 0.2f) facingLeft = nx < 0f
    }

    fun update(dt: Float, p: Player, world: Collider, proj: MutableList<Projectile>) {
        if (isDead) {
            deadA.update(dt)
            deadTimer += dt
            return
        }
        idleA.update(dt)
        walkA.update(dt)
        if (hurtTimer > 0f) hurtTimer -= dt
        if (cooldown > 0f) cooldown -= dt
        if (kx != 0f || ky != 0f) {
            world.move(this, kx * dt, ky * dt)
            val k = min(1f, 9f * dt)
            kx -= kx * k
            ky -= ky * k
            if (abs(kx) < 6f) kx = 0f
            if (abs(ky) < 6f) ky = 0f
        }
        hitbox.set(x + size * 0.25f, y + size * 0.2f, x + size * 0.75f, y + size * 0.95f)
        val dx = p.cx - cx
        val dy = p.cy - cy
        val dist = sqrt(dx * dx + dy * dy)
        moving = false
        if (p.isDead) return
        if (!aggro && dist < 560f) aggro = true
        if (aggro && !isBoss && dist > 1150f) aggro = false

        if (windup > 0f) {
            windup -= dt
            attackA.update(dt)
            if (windup <= 0f) finishAttack(p, proj, dx, dy, dist)
            return
        }
        if (!aggro) {
            val hx = homeX - x
            val hy = homeY - y
            if (hx * hx + hy * hy > 900f) stepTo(hx, hy, 0.6f, dt, world)
            return
        }
        facingLeft = dx < 0f
        if (isBoss) {
            volleyTimer -= dt
            if (volleyTimer <= 0f && dist < 800f) {
                volley(proj, dx, dy)
                volleyTimer = if (phase2) 2.4f else 3.6f
            }
            if (phase2) {
                summonTimer -= dt
                if (summonTimer <= 0f) {
                    wantSummon = true
                    summonTimer = 11f
                }
            }
        }
        if (ranged) {
            shootTimer -= dt
            if (dist < 220f) stepTo(-dx, -dy, 1f, dt, world)
            else if (dist > 430f) stepTo(dx, dy, 1f, dt, world)
            else stepTo(-dy * sideSign, dx * sideSign, 0.5f, dt, world)
            if (shootTimer <= 0f && dist < 640f) {
                windup = 0.4f
                attackA.reset()
                shootTimer = 2.1f + (x.toInt() % 5) * 0.12f
            }
        } else {
            if (dist > reach * 0.85f) {
                stepTo(dx, dy, 1f, dt, world)
            } else if (cooldown <= 0f) {
                windup = if (isBoss) 0.5f else 0.42f
                cooldown = if (isBoss) 1.3f else 1.1f
                attackA.reset()
            }
        }
    }

    private fun finishAttack(p: Player, proj: MutableList<Projectile>, dx: Float, dy: Float, dist: Float) {
        if (ranged) {
            val l = max(1f, dist)
            val kind = if (role == EnemyRole.COMMANDO) Projectile.LASER else Projectile.ARROW
            proj.add(Projectile(cx, cy, dx / l * 360f, dy / l * 360f, damage, kind, false))
        } else {
            if (dist < reach * 1.4f) p.takeDamage(damage)
        }
    }

    private fun volley(proj: MutableList<Projectile>, dx: Float, dy: Float) {
        val base = atan2(dy, dx)
        val laser = era == EraType.ERA_5_CYBER
        val kind = if (laser) Projectile.LASER else Projectile.ORB
        val dmg = (damage * 0.5f).toInt()
        if (phase2 && era == EraType.ERA_6_VOID) {
            for (i in 0 until 12) {
                val a = base + i * (Math.PI.toFloat() * 2f / 12f)
                proj.add(Projectile(cx, cy, cos(a) * 300f, sin(a) * 300f, dmg, kind, false))
            }
            return
        }
        val n = if (phase2) 7 else 5
        for (i in 0 until n) {
            val a = base + (i - n / 2) * 0.27f
            proj.add(Projectile(cx, cy, cos(a) * 330f, sin(a) * 330f, dmg, kind, false))
        }
    }

    fun draw(c: Canvas, time: Float) {
        Fx.oval.set(x + size * 0.22f, y + size * 0.80f, x + size * 0.78f, y + size * 0.98f)
        c.drawOval(Fx.oval, Fx.shadow)
        val sheet = when {
            isDead -> deadA
            windup > 0f -> attackA
            moving -> walkA
            else -> idleA
        }
        val paint: Paint? = if (hurtTimer > 0f) Fx.white else if (windup > 0f) Fx.red else tintPaint
        sheet.draw(c, x, y, size, size, facingLeft, paint)
        if (isDead) return
        if (shielded) {
            Fx.slash.color = Color.CYAN
            Fx.slash.alpha = (150 + 80 * sin(time * 6f)).toInt().coerceIn(0, 255)
            Fx.slash.strokeWidth = 5f
            c.drawCircle(cx, cy - size * 0.1f, size * 0.55f, Fx.slash)
            Fx.slash.strokeWidth = 8f
        }
        if (!isBoss && hp < maxHp) {
            val bw = 54f
            val bx = x + size / 2f - bw / 2f
            val by = y + size * 0.08f
            Fx.bar.color = Color.argb(200, 0, 0, 0)
            c.drawRect(bx - 2f, by - 2f, bx + bw + 2f, by + 8f, Fx.bar)
            Fx.bar.color = if (hp > maxHp / 3) Color.rgb(230, 70, 60) else Color.rgb(255, 160, 40)
            c.drawRect(bx, by, bx + bw * hp / maxHp, by + 6f, Fx.bar)
        }
        if (holdsKey) {
            val k = Assets.bmp("icons/key.png")
            if (k != null) {
                val bob = sin(time * 4f) * 4f
                Fx.oval.set(x + size / 2f - 14f, y - 14f + bob, x + size / 2f + 14f, y + 14f + bob)
                c.drawBitmap(k, null, Fx.oval, null)
            }
        }
    }
}
