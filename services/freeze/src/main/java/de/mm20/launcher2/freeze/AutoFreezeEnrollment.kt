package de.mm20.launcher2.freeze

/** Inventory is read-only; enrolling it never thaws apps or overrides a never-freeze policy. */
fun autoFreezeImportCandidates(
    inventory: List<FrozenAppInventory>,
    userId: Int,
    candidates: Set<String>,
    neverFreeze: Set<String>,
    ownPackage: String,
): Set<String> =
    inventory
        .asSequence()
        .map { it.state }
        .filter { it.userId == userId && it.packageName != ownPackage }
        .map { it.packageName }
        .filter { it !in candidates && it !in neverFreeze }
        .toSet()
