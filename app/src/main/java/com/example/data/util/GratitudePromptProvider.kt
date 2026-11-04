package com.example.data.util

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * Provides deterministic daily rotating gratitude prompts so users receive a thoughtful,
 * consistent reflection question or statement that changes automatically every calendar day.
 */
object GratitudePromptProvider {

    val prompts: List<String> = listOf(
        "What is one small thing that brought you joy today?",
        "List three things you are grateful for right now.",
        "Who is someone whose kindness made your week lighter, and why?",
        "What quiet moment from today would you love to relive?",
        "Name a personal strength or habit that helped you navigate today.",
        "What sensory detail—a scent, flavor, song, or view—are you thankful for today?",
        "Reflect on a challenge that taught you something valuable recently.",
        "What comfort in your home or daily routine do you appreciate most right now?",
        "Who believed in you or encouraged you when you needed it?",
        "What is something in nature or outside your window that inspired calm today?",
        "Describe one small victory or completed task you feel proud of today.",
        "What meal, drink, or nourishment were you grateful to enjoy today?",
        "Think of a friend or family member—what quality in them do you treasure?",
        "What opportunity or freedom did you have today that you didn't have years ago?",
        "What made you smile or laugh unexpectedly in the past 24 hours?",
        "Name a book, idea, or conversation that enriched your perspective recently.",
        "What part of your health or body supported you through today?",
        "What peaceful boundary or moment of rest did you give yourself today?",
        "Identify one tool, skill, or craft in your life that makes daily living smoother.",
        "What memory from this month still warms you when you think about it?",
        "Who in your community or workplace quietly makes things better for everyone?",
        "What upcoming moment, big or small, are you thankful to look forward to?",
        "Reflect on a mistake that turned into an unexpected blessing or lesson.",
        "What act of generosity—given or received—stood out to you recently?",
        "List three simple pleasures that cost nothing yet made today richer.",
        "What place have you visited or lived in that shaped who you are today?",
        "What sound—rain, laughter, music, or morning quiet—brought you peace recently?",
        "How have you grown emotionally or creatively over the past year?",
        "What piece of advice or wisdom are you still thankful someone shared with you?",
        "Before today ends, what is one thing that went right that deserves recognition?",
        "Which routine or ritual grounds you when life feels busy?"
    )

    fun getPromptForDate(date: LocalDate = LocalDate.now(ZoneId.systemDefault())): String {
        val epochDay = date.toEpochDay()
        // Deterministic spread so consecutive days always differ cleanly
        val index = abs((epochDay * 7L + 3L) % prompts.size.toLong()).toInt()
        return prompts[index]
    }

    fun getPromptForEpochMs(epochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val localDate = java.time.Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalDate()
        return getPromptForDate(localDate)
    }
}
