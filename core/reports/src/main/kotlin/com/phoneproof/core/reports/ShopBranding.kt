package com.phoneproof.core.reports

/**
 * A shop's own details, printed on a report they hand to a customer.
 *
 * The Shop tier exists because a repair shop or a dealer inspecting stock wants to give the customer
 * something with their name on it. That only works if the branding is unmistakably *theirs* and the
 * measurements are unmistakably the app's — a report a shop could pass off entirely as its own would
 * let a dishonest one attach its name to numbers it had edited.
 *
 * So branding is a header, never a replacement for the footer that says where the numbers came from.
 * [ReportDocument] enforces that by always emitting the provenance line.
 *
 * @param logoPath absolute path to an image the shop chose, copied into app storage. Null when none
 *   has been set, which is the normal case.
 */
data class ShopBranding(
    val name: String? = null,
    /** A phone number, address or website. One line, printed under the name. */
    val contact: String? = null,
    val logoPath: String? = null,
) {
    val hasAnything: Boolean
        get() = !name.isNullOrBlank() || !contact.isNullOrBlank() || !logoPath.isNullOrBlank()

    companion object {
        val None = ShopBranding()

        /** Longer than this and it stops fitting a page header beside a logo. */
        const val MAX_NAME_LENGTH: Int = 40
        const val MAX_CONTACT_LENGTH: Int = 60
    }
}
