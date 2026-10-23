package com.uniquelo.app.data

/**
 * Supabase project credentials.
 *
 * This is a *publishable* client key (the replacement for the legacy anon key)
 * and is designed to be shipped in client apps. It only grants what the
 * project's row level security policies allow: public reads and public order /
 * newsletter inserts. Never put a `service_role` key in an app.
 */
object SupabaseConfig {
    const val URL = "https://heimnfjuwmmmejqcjbbd.supabase.co"
    const val PUBLISHABLE_KEY = "sb_publishable_q9z2aAMjmAWkpg8ADKJ69A_0UqVIGEZ"
    const val REST_URL = "$URL/rest/v1"
}
