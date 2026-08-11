package com.phoneproof.core.model

/**
 * Renders a count with a correctly pluralised noun: "1 month", "3 months".
 *
 * Exists because the alternative was appearing on screen as "1 month(s)" and "1 app(s)". In an app
 * whose entire proposition is that a stranger should trust its verdict, copy that reads like an
 * unfinished template undermines the finding next to it — a buyer who thinks the software is sloppy
 * will not use it to argue about money.
 *
 * Deliberately not `getQuantityString`: these strings are produced inside pure-Kotlin check modules
 * that have no `Context` and no resources, which is what keeps them testable in milliseconds. When
 * translation arrives this becomes the single place that has to change.
 */
fun plural(count: Int, singular: String, plural: String = singular + "s"): String =
    "$count ${nounFor(count, singular, plural)}"

/** The noun alone, for sentences that place the number elsewhere. */
fun nounFor(count: Int, singular: String, plural: String = singular + "s"): String =
    if (count == 1 || count == -1) singular else plural
