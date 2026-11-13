package com.clashangprpr.app.model

import android.graphics.Color

class CraftRecipe(
    val id: String,
    val titleAr: String,
    val descAr: String,
    val resultType: String,
    val iconAsset: String,
    val tint: Int? = null,
    val repeatable: Boolean = false,
    val wood: Int = 0,
    val stone: Int = 0,
    val iron: Int = 0,
    val gold: Int = 0,
    val mats: IntArray = IntArray(6),
    val wool: Int = 0
)

/** Resolved cost of a recipe (upgrades get more expensive with every level). */
class Cost(
    val wood: Int,
    val stone: Int,
    val iron: Int,
    val gold: Int,
    val mats: IntArray,
    val wool: Int = 0
)

object CraftingRegistry {
    const val MAX_UPG = 6

    val recipes = listOf(
        CraftRecipe(
            "craft_blk_wood", "ألواح خشبية للبناء (x6)", "بلوكات جدران خشبية لبناء البيوت والغرف على الخريطة.",
            "BLK_WOOD", "icons/block_wood.png", null, true, 2, 0, 0, 0
        ),
        CraftRecipe(
            "craft_blk_stone", "طوب حجري للبناء (x6)", "بلوكات طوب حجري لبناء القلاع والأسوار المنيعة.",
            "BLK_STONE", "icons/block_stone_brick.png", null, true, 0, 2, 0, 0
        ),
        CraftRecipe(
            "craft_blk_bridge", "جسر وأرضية خشبية (x6)", "توضع على الأرض أو فوق المياه العميقة لبناء الجسور والموانئ!",
            "BLK_BRIDGE", "icons/block_bridge.png", null, true, 2, 0, 0, 0
        ),
        CraftRecipe(
            "craft_blk_door", "باب منزل خشبي (x2)", "باب يفتح ويغلق عند التفاعل معه ويمنع الوحوش.",
            "BLK_DOOR", "tiles/door.png", null, true, 2, 0, 0, 0
        ),
        CraftRecipe(
            "craft_blk_fence", "سياج دفاعي (x6)", "سياج يحمي مزرعتك وقريتك ويسمح بمرور ضرباتك.",
            "BLK_FENCE", "icons/block_fence.png", null, true, 2, 0, 0, 0
        ),
        CraftRecipe(
            "craft_blk_torch", "مشاعل إضاءة (x6)", "توضع في البيوت والكهوف لطرد الظلام ليلاً.",
            "BLK_TORCH", "environment/torch.png", null, true, 1, 1, 0, 0
        ),
        CraftRecipe(
            "craft_blk_tnt", "بلوك متفجرات TNT (x2)", "ينفجر عند ضربه ويدمر الصخور والحصون والوحوش!",
            "BLK_TNT", "icons/block_tnt.png", null, true, 1, 2, 1, 10
        ),
        CraftRecipe(
            "craft_bed", "سرير النوم الميداني", "يُصنع من الصوف والخشب. ضعه من الحقيبة للنوم والحفظ في أي مكان!",
            "BED", "icons/bed.png", null, true, 4, 0, 0, 0, IntArray(6), 3
        ),
        CraftRecipe(
            "craft_furnace", "فرن الصهر والطبخ", "يوضع في العالم لطهي اللحوم مجاناً وصهر الحديد والحجر إلى ذهب وزجاج.",
            "FURNACE", "icons/furnace.png", null, true, 0, 6, 3, 0
        ),
        CraftRecipe(
            "craft_boat", "مركب الإبحار السريع", "يتيح لك الإبحار فوق المياه العميقة والوصول للجزر وحصون القراصنة!",
            "BOAT", "icons/boat.png", null, false, 8, 0, 1, 20, IntArray(6), 2
        ),
        CraftRecipe(
            "craft_cannon", "مدفع الحراسة الآلي", "مدفع يوضع في القرية أو قاعدتك ويطلق قذائف تلقائياً على الزومبي والقراصنة.",
            "CANNON", "icons/pirate_cannon.png", null, true, 4, 2, 4, 35
        ),
        CraftRecipe(
            "craft_campfire", "مخيم السفر السريع", "يبني نار مخيم تفتح نقطة سفر سريع وتطرد وحوش الليل.",
            "CAMPFIRE", "environment/bonefire.png", null, true, 5, 3, 0, 0
        ),
        CraftRecipe(
            "craft_pickaxe", "فأس التعدين والتكسير", "أداة لتكسير البلوكات واستخراج الكنوز المدفونة وتعدين الكهوف.",
            "PICKAXE", "ui/ico/axe.png", Color.parseColor("#FFB74D"), false, 3, 2, 2, 0
        ),
        CraftRecipe(
            "craft_key", "مفتاح ذهبي", "يفتح صناديق الغنائم الذهبية في الحصون وطوابق الكهوف.",
            "GOLD_KEY", "icons/key_gold.png", null, true, 0, 0, 2, 45
        ),
        CraftRecipe(
            "craft_stew", "حساء الطاقة الساخن (x2)", "وجبة مطبوخة تملأ الشبع +75 وتستعيد +35 صحة.",
            "STEW", "icons/stew.png", null, true, 2, 0, 0, 15
        ),
        CraftRecipe(
            "craft_speed_pot", "إكسير السرعة والرؤية (x2)", "يضاعف سرعة الحركة ويكشف ظلام الكهوف والليل.",
            "SPEED_POT", "icons/pot_speed.png", null, true, 0, 2, 1, 25
        ),
        CraftRecipe(
            "craft_bomb", "قنبلتان (x2)", "تنفجر بمساحة 150 وتكسر الأشجار والصخور والحصون.",
            "BOMB", "icons/bomb.png", null, true, 2, 2, 2, 15
        ),
        CraftRecipe(
            "upg_weapon", "صقل السلاح (الحداد)", "+12% ضرر لكل مستوى. يتطلب خامات العصور.",
            "UPG_WEAPON", "icons/sabre.png", null, true
        ),
        CraftRecipe(
            "upg_armor", "تقوية الدرع (الحداد)", "+25 صحة و+2% حماية لكل مستوى.",
            "UPG_ARMOR", "ui/ico/shield.png", Color.parseColor("#FFD54F"), true
        ),
        CraftRecipe(
            "craft_plasma", "مدفع البلازما", "السحر يصبح ليزر ضرره 125 ويخترق الأعداء.",
            "PLASMA", "icons/cannon.png", null, false, 0, 4, 5, 90,
            intArrayOf(0, 4, 3, 0, 0, 0)
        ),
        CraftRecipe(
            "craft_shield", "درع النانو", "+160 صحة قصوى ويقلل الضرر 42%.",
            "SHIELD", "ui/ico/shield.png", Color.parseColor("#4DD0E1"), false, 3, 0, 6, 120,
            intArrayOf(0, 0, 5, 3, 0, 0)
        ),
        CraftRecipe(
            "craft_godblade", "نصل الأبعاد الكوني", "أقوى سلاح: ضرر 170، والسحر يصبح 170 ويخترق.",
            "GODBLADE", "icons/blade.png", null, false, 5, 5, 6, 250,
            intArrayOf(0, 6, 6, 5, 5, 0)
        )
    )
}
