package com.clashangprpr.app.model

import android.graphics.Color

data class Weapon(
    val id: String,
    val nameAr: String,
    val damage: Int,
    val reach: Float,
    val cooldown: Float,
    val iconAsset: String,
    val color: Int
)

data class Armor(
    val id: String,
    val nameAr: String,
    val hpBonus: Int,
    val reduction: Float
)

/** Placeable Minecraft-style block definitions for the Open World grid. */
enum class BlockType(
    val code: Int,
    val nameAr: String,
    val descAr: String,
    val tileAsset: String,
    val iconAsset: String,
    val solid: Boolean,
    val bridge: Boolean = false
) {
    WOOD_WALL(1, "بلوك ألواح خشبية", "جدار خشبي متين لبناء المنازل والغرف.", "tiles/block_wood.png", "icons/block_wood.png", true),
    STONE_BRICK(2, "بلوك طوب حجري", "جدار حجري صلب لبناء القلاع والحصون.", "tiles/block_stone_brick.png", "icons/block_stone_brick.png", true),
    WOOD_BRIDGE(3, "أرضية / جسر خشبي", "أرضية خشبية يمكن بناؤها فوق الأرض أو فوق الماء كجسر!", "tiles/block_bridge.png", "icons/block_bridge.png", false, true),
    DOOR_CLOSED(4, "باب منزل خشبي", "باب يفتح ويغلق بالضغط عليه، ويمنع دخول الوحوش.", "tiles/door.png", "tiles/door.png", false),
    FENCE(6, "سياج دفاعي", "سياج خشبي يمنع مرور الأعداء ويسمح بمرور السهام والسحر.", "tiles/block_fence.png", "icons/block_fence.png", true),
    TORCH(7, "شعلة إضاءة", "تضيء المكان حولها في الليل وداخل الكهوف.", "environment/candlestick.png", "environment/torch.png", false),
    GLASS_BLOCK(8, "بلوك زجاج بلوري", "جدار زجاجي شفاف للنوافذ والقلاع الحديثة.", "tiles/block_glass.png", "icons/block_glass.png", true),
    TNT_BLOCK(9, "بلوك متفجرات TNT", "ينفجر بقوة هائلة عند ضربه بالسيف أو الفأس أو النار!", "tiles/block_tnt.png", "icons/block_tnt.png", true);

    companion object {
        fun byCode(c: Int): BlockType? = values().firstOrNull { it.code == c }
    }
}

enum class SpellType(
    val id: String,
    val nameAr: String,
    val descAr: String,
    val manaCost: Float,
    val cooldown: Float,
    val iconAsset: String,
    val color: Int
) {
    ARCANE_BOLT("bolt", "قذيفة الزمن", "تطلق كرة طاقة سريعة تخترق الظلام.", 20f, 0.42f, "ui/ico/cross.png", Color.parseColor("#00E5FF")),
    FIRE_NOVA("nova", "عاصفة اللهب", "تطلق 8 كرات نارية في كل الاتجاهات تحرق الأعداء.", 32f, 1.4f, "icons/pot17.png", Color.parseColor("#FF7043")),
    FROST_HEAL("heal", "نبض النور والدرع", "تستعيد +40 صحة وتمنح درع حماية سحري مؤقت.", 38f, 3.2f, "icons/pot_shield.png", Color.parseColor("#69F0AE")),
    VOID_LASER("laser", "شعاع الفراغ الخارق", "ليزر كوني خارق يخترق صفوف الأعداء بضرر هائل.", 28f, 0.75f, "icons/pot_vision.png", Color.parseColor("#E040FB"))
}

enum class ItemType { WEAPON, ARMOR, CONSUMABLE, UPGRADE }

data class ShopItem(
    val id: String,
    val titleAr: String,
    val descAr: String,
    val price: Int,
    val type: ItemType,
    val iconAsset: String,
    val tint: Int? = null,
    val maxCount: Int = 1
)

class Quest(
    val id: String,
    val titleAr: String,
    val descAr: String,
    val targetCount: Int,
    val rewardGold: Int,
    val rewardMatIdx: Int,
    val rewardMatCount: Int,
    val iconAsset: String
) {
    var progress: Int = 0
    var claimed: Boolean = false
    val isComplete: Boolean get() = progress >= targetCount
}

object QuestBoard {
    fun createDefaultQuests(): List<Quest> = listOf(
        Quest("q_build", "مهندس البناء الحر", "ضع 10 بلوكات بناء (جدران، جسور، أبواب، أو مشاعل) في العالم المفتوح.", 10, 140, 0, 3, "icons/block_wood.png"),
        Quest("q_zombie", "تطهير الزومبي والوحوش", "اقضِ على 8 من الزومبي أو وحوش المناطق لحماية القرى.", 8, 120, 2, 2, "icons/monster_1.png"),
        Quest("q_wool", "جزّ الخراف وجمع الصوف", "اجمع 4 قطع صوف من الخراف في المروج لصناعة السرير والمراكب.", 4, 90, 0, 2, "icons/wool.png"),
        Quest("q_cave", "مستكشف الكهوف المظلمة", "ادخل أحد الكهوف وافتح صندوقاً أو دمر عش وحوش بالداخل.", 2, 160, 1, 3, "environment/crystal_green.png"),
        Quest("q_pirate", "غارة جزر القراصنة", "أبحر بالمركب إلى الجزر واهزم 5 من القراصنة أو حراس الحصون.", 5, 180, 3, 3, "icons/pirate_flag.png"),
        Quest("q_cook", "خبير الفرن والصهر", "اطبخ أو اصهر 4 مرات في الفرن التفاعلي.", 4, 100, 0, 2, "icons/furnace.png"),
        Quest("q_treasure", "صائد الكنوز المدفونة", "اتبع البوصلة واحفر كنزاً مدفوناً أو افتح صندوق حصن ذهبي.", 1, 200, 4, 2, "icons/chest_gold.png"),
        Quest("q_trade", "التجارة والمقايضة", "قم بالتبادل مع القرويين لرفع مستواهم التجاري 3 مرات.", 3, 120, 5, 2, "icons/goblet.png")
    )
}

object GameCatalogue {
    val WEAPON_BASIC = Weapon("sword_basic", "سيف الفجر", 35, 84f, 0.30f, "icons/item_1.png", Color.WHITE)
    val WEAPON_DAGGER = Weapon("dagger_silver", "خنجر الفضة السريع", 52, 80f, 0.18f, "icons/dagger_silver.png", Color.parseColor("#B0BEC5"))
    val WEAPON_FIRE = Weapon("sword_fire", "نصل النار الملتهب", 65, 92f, 0.28f, "icons/item_2.png", Color.parseColor("#FF7043"))
    val WEAPON_PIRATE = Weapon("sword_pirate", "سيف القبطان الذهبي", 85, 98f, 0.26f, "icons/sword_gold.png", Color.parseColor("#FFD54F"))
    val WEAPON_CHRONO = Weapon("spear_chrono", "رمح كرونو الأسطوري", 105, 122f, 0.36f, "icons/item_5.png", Color.parseColor("#00E5FF"))
    val WEAPON_GOD = Weapon("godblade", "نصل الأبعاد الكوني", 170, 110f, 0.30f, "icons/blade.png", Color.parseColor("#E040FB"))

    val weapons = listOf(WEAPON_BASIC, WEAPON_DAGGER, WEAPON_FIRE, WEAPON_PIRATE, WEAPON_CHRONO, WEAPON_GOD)

    val ARMOR_ROYAL = Armor("armor_royal", "درع الفارس الملكي", 100, 0.30f)
    val ARMOR_NANO = Armor("armor_nano", "درع النانو المتطور", 160, 0.42f)
    val armors = listOf(ARMOR_ROYAL, ARMOR_NANO)

    fun weaponById(id: String): Weapon? = weapons.firstOrNull { it.id == id }
    fun armorById(id: String): Armor? = armors.firstOrNull { it.id == id }

    val shopItems = listOf(
        ShopItem("dagger_silver", "خنجر الفضة السريع", "ضربات سريعة جداً (+فرصة نجاح سرقة أعلى) ضرر 52.", 85, ItemType.WEAPON, "icons/dagger_silver.png"),
        ShopItem("sword_fire", "نصل النار الملتهب", "سيف حارق. ضرر 65 ومدى أطول.", 130, ItemType.WEAPON, "icons/item_2.png"),
        ShopItem("sword_pirate", "سيف القبطان الذهبي", "سيف القراصنة الملكي: ضرر 85 وسرعة عالية.", 220, ItemType.WEAPON, "icons/sword_gold.png"),
        ShopItem("spear_chrono", "رمح كرونو الأسطوري", "أطول مدى وأقوى ضربة: ضرر 105.", 340, ItemType.WEAPON, "icons/item_5.png"),
        ShopItem("armor_royal", "درع الفارس الملكي", "+100 صحة قصوى ويقلل الضرر 30%.", 180, ItemType.ARMOR, "ui/ico/shield.png", Color.parseColor("#FFD54F")),
        ShopItem("potion_heal", "جرعتا شفاء", "تستعيد 85 صحة. المخزون يتجدد يومياً.", 35, ItemType.CONSUMABLE, "icons/pot1.png"),
        ShopItem("pot_speed", "إكسير السرعة والرؤية", "يمنح سرعة حركة مضاعفة ورؤية في ظلام الكهوف لمدة 45 ثانية.", 45, ItemType.CONSUMABLE, "icons/pot_speed.png"),
        ShopItem("stew_pack", "حساء الطاقة الساخن (x2)", "يشبع الجوع +75 ويستعيد +35 صحة فوراً.", 40, ItemType.CONSUMABLE, "icons/stew.png"),
        ShopItem("bombs", "قنبلتان متفجرتان", "انفجار بمساحة يدمر الصخور والأعداء.", 50, ItemType.CONSUMABLE, "icons/bomb.png"),
        ShopItem("key_gold", "مفتاح الصناديق الذهبي", "يفتح صناديق الحصون والكهوف الذهبية المقفلة.", 65, ItemType.CONSUMABLE, "icons/key_gold.png"),
        ShopItem("treasure_map", "خريطة كنز مفقود", "تكشف موقع كنز مدفون جديد على البوصلة.", 55, ItemType.CONSUMABLE, "icons/map.png"),
        ShopItem("up_hp", "قلب الحياة", "+30 صحة قصوى دائمة. يغلو السعر مع كل مرة.", 150, ItemType.UPGRADE, "icons/pot33.png", null, 5),
        ShopItem("up_mana", "حجر الطاقة السحرية", "+25 طاقة سحر قصوى ويفتح قدرات الكتاب السحري.", 125, ItemType.UPGRADE, "icons/pot10.png", null, 5)
    )
}

/** One special material per era; needed for the strong recipes and for upgrades. */
object Mats {
    val names = arrayOf("صلب الحصن", "بلورة الكهف", "خردة المدينة", "لؤلؤ البحر", "رقاقة سايبر", "شظية الفراغ")
    val paths = arrayOf(
        "icons/iron.png", "environment/crystal_green.png", "icons/stone.png",
        "environment/crystal_white.png", "environment/crystal_white.png", "environment/crystal_white.png"
    )
    val mul = intArrayOf(
        0xFFFFB74D.toInt(), 0xFFFFFFFF.toInt(), 0xFFC8A070.toInt(),
        0xFFFFC0E0.toInt(), 0xFF50E0FF.toInt(), 0xFFD070FF.toInt()
    )
}
