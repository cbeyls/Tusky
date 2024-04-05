package com.keylesspalace.tusky.network

import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.entity.FilterV1
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.util.parseAsMastodonHtml
import java.util.Date
import javax.inject.Inject

/**
 * One-stop for status filtering logic using Mastodon's filters.
 *
 * 1. You init with [initWithFilters], this compiles regex pattern.
 * 2. You call [shouldFilterStatus] to figure out what to display when you load statuses.
 */
class FilterModel @Inject constructor() {
    private var regex: Regex? = null
    private var v1 = false
    lateinit var kind: Filter.Kind

    fun initWithFilters(filters: List<FilterV1>) {
        v1 = true
        this.regex = makeFilter(filters)
    }

    fun shouldFilterStatus(status: Status): Filter.Action {
        if (v1) {
            val currentRegex = regex ?: return Filter.Action.NONE

            if (status.poll?.options?.any { currentRegex.find(it.title) != null } == true) {
                return Filter.Action.HIDE
            }

            val spoilerText = status.actionableStatus.spoilerText
            val attachmentsDescriptions = status.attachments.mapNotNull { it.description }

            return if (
                currentRegex.find(status.actionableStatus.content.parseAsMastodonHtml()) != null ||
                (spoilerText.isNotEmpty() && currentRegex.find(spoilerText) != null) ||
                (attachmentsDescriptions.isNotEmpty() && currentRegex.find(attachmentsDescriptions.joinToString("\n")) != null)
            ) {
                Filter.Action.HIDE
            } else {
                Filter.Action.NONE
            }
        }

        val matchingKind = status.filtered.filter { result ->
            result.filter.kinds.contains(kind)
        }

        return if (matchingKind.isEmpty()) {
            Filter.Action.NONE
        } else {
            matchingKind.maxOf { it.filter.action }
        }
    }

    private fun filterToRegexToken(filter: FilterV1): String {
        val phrase = filter.phrase
        val quotedPhrase = Regex.escape(phrase)
        return if (filter.wholeWord && ALPHANUMERIC.matches(phrase)) {
            String.format("(^|\\W)%s($|\\W)", quotedPhrase)
        } else {
            quotedPhrase
        }
    }

    private fun makeFilter(filters: List<FilterV1>): Regex? {
        val now = Date()
        val nonExpiredFilters = filters.filter { it.expiresAt?.before(now) != true }
        if (nonExpiredFilters.isEmpty()) return null
        val tokens = nonExpiredFilters
            .asSequence()
            .map { filterToRegexToken(it) }
            .joinToString("|")

        return Regex(tokens, RegexOption.IGNORE_CASE)
    }

    companion object {
        private val ALPHANUMERIC = Regex("^\\w+$")
    }
}
