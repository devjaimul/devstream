package com.devsstream.cafepos.core.validation

import com.devsstream.cafepos.core.catalog.Catalog
import com.devsstream.cafepos.core.catalog.ModifierGroup
import com.devsstream.cafepos.core.catalog.OptionRule
import com.devsstream.cafepos.core.catalog.Product
import com.devsstream.cafepos.core.order.ItemSelection

// id is the group id, rule id, or "size"
data class Violation(val id: String, val message: String)

const val SIZE_ID = "size"

fun validate(selection: ItemSelection, product: Product, catalog: Catalog): List<Violation> {
    require(selection.productId == product.id) {
        "selection is for ${selection.productId}, not ${product.id}"
    }
    // quantity 0 means the cashier un-ticked it
    val picked = selection.options.filterValues { it > 0 }
    val name = { optionId: String -> catalog.option(optionId)?.name ?: optionId }

    return buildList {
        addAll(checkSize(selection, product))

        for (optionId in picked.keys) {
            val group = catalog.groupOf(optionId)
            when {
                group == null -> add(Violation(optionId, "Unknown option $optionId"))
                group.id !in product.groupIds -> add(Violation(group.id, "${product.name} doesn't take ${group.name}"))
            }
        }

        for (groupId in product.groupIds) {
            val group = catalog.group(groupId) ?: continue
            val inGroup = picked.filterKeys { id -> group.options.any { it.id == id } }
            if (inGroup.isNotEmpty()) addAll(checkLimits(group, inGroup, name))
        }

        for (rule in catalog.rules) {
            if (rule.optionId !in picked) continue
            when (rule) {
                is OptionRule.Requires ->
                    if (rule.anyOf.none { it in picked }) {
                        val needed = rule.anyOf.joinToString(" or ") { name(it) }
                        add(Violation(rule.id, "${name(rule.optionId)} requires $needed"))
                    }

                is OptionRule.Excludes -> {
                    val clash = rule.others.filter { it in picked }
                    if (clash.isNotEmpty()) {
                        val others = clash.joinToString(", ") { name(it) }
                        add(Violation(rule.id, "${name(rule.optionId)} can't be combined with $others"))
                    }
                }
            }
        }
    }
}

private fun checkSize(selection: ItemSelection, product: Product): List<Violation> {
    val sizeId = selection.sizeId
    return when {
        product.sizes.isEmpty() && sizeId != null ->
            listOf(Violation(SIZE_ID, "${product.name} has no sizes"))

        product.sizes.isNotEmpty() && sizeId == null ->
            listOf(Violation(SIZE_ID, "A size is required for ${product.name}"))

        sizeId != null && product.sizes.none { it.id == sizeId } ->
            listOf(Violation(SIZE_ID, "${product.name} has no size $sizeId"))

        else -> emptyList()
    }
}

private fun checkLimits(
    group: ModifierGroup,
    picked: Map<String, Int>,
    name: (String) -> String,
): List<Violation> = buildList {
    val total = picked.values.sum()
    group.max?.let { max ->
        if (total > max) add(Violation(group.id, "${group.name}: at most $max, got $total"))
    }
    group.maxDistinct?.let { max ->
        if (picked.size > max) add(Violation(group.id, "${group.name}: at most $max different, got ${picked.size}"))
    }
    group.maxPerOption?.let { max ->
        picked.filterValues { it > max }.forEach { (id, qty) ->
            add(Violation(group.id, "${group.name}: at most $max of ${name(id)}, got $qty"))
        }
    }
}
