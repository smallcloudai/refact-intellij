package com.smallcloud.refactai.statistic.utils

private val GIT_DEFAULTS = mapOf("user" to "git")

// BASE
private val BASE_FORMATS: Map<String, String> = mapOf(
        "ssh" to "git@%(domain):%(repo)%(dotGit)%(pathRaw)",
        "https" to "https://%(domain)/%(repo)%(dotGit)",
        "git" to "git://%(domain)/%(repo)%(dotGit)%(pathRaw)"
)
private val BASE_PATTERNS: Map<String, String> = mapOf(
        "ssh" to "(?<user>.+)@(?<domain>[^/]+?):(?<repo>.+)(?:(.git)?(/)?)",
        "http" to "(?<protocols>(?<protocol>http))://(?<domain>[^/]+?)/(?<repo>.+)(?:(.git)?(/)?)",
        "https" to "(?<protocols>(?<protocol>https))://(?<domain>[^/]+?)/(?<repo>.+)(?:(.git)?(/)?)",
        "git" to "(?<protocols>(?<protocol>git))://(?<domain>[^/]+?)/(?<repo>.+)(?:(.git)?(/)?)"
)
private val BASE_DOMAINS: List<String> = listOf()
private val BASE_DEFAULTS: Map<String, String> = mapOf()
private fun baseCleanData(data: MutableMap<String, Any?>): MutableMap<String, Any?>{
    data["path"] = ""
    data["branch"] = ""
    data["protocols"] = (data.getOrDefault("protocols", "") as String).split("+").toList()
    data["pathname"] = (data.getOrDefault("pathname", "") as String).trim(':').trimEnd('/')
    return data
}

// ASSEMBLA
private val ASSEMBLA_DOMAINS: List<String> = listOf("git.assembla.com")
private val ASSEMBLA_PATTERNS = mapOf(
    "ssh" to "(?<protocols>(git\\+)?(?<protocol>ssh))?(://)?git@(?<domain>.+?):(?<pathname>(?<repo>.+)).git",
    "git" to "(?<protocols>(?<protocol>git))://(?<domain>.+?)/(?<pathname>(?<repo>.+)).git",
)
private val ASSEMBLA_FORMATS: Map<String, String> = mapOf(
    "ssh" to "git@%(domain):%(repo)%(dotGit)",
    "git" to "git://%(domain)/%(repo)%(dotGit)",
)

// BITBUCKET
private val BITBUCKET_PATTERNS = mapOf(
    "https" to "(?<protocols>(git+)?(?<protocol>https))://(?<user>.+)@(?<domain>.+?)" +
            "(?<pathname>/(?<owner>.+)/(?<repo>.+?)(?:.git)?)\$",
    "ssh" to "(?<protocols>(git+)?(?<protocol>ssh))?(://)?git@(?<domain>.+?):" +
            "(?<pathname>(?<owner>.+)/(?<repo>.+?)(?:.git)?)\$"
)
private val BITBUCKET_FORMATS = mapOf(
    "https" to "https://%(owner)@%(domain)/%(owner)/%(repo)%(dotGit)",
    "ssh" to "git@%(domain):%(owner)/%(repo)%(dotGit)"
)
private val BITBUCKET_DOMAINS = listOf("bitbucket.org")

// GITHUB
private val GITHUB_PATTERNS = mapOf(
    "https" to "(?<protocols>(git+)?(?<protocol>https))://(?<domain>[^/]+?)" +
            "(?<pathname>/(?<owner>[^/]+?)/(?<repo>[^/]+?)(?:(.git)?(/)?)(?<pathRaw>(/blob/|/tree/).+)?)\$",
    "ssh" to "(?<protocols>(git+)?(?<protocol>ssh))?(://)?git@(?<domain>.+?)(?<pathname>(:|/)" +
            "(?<owner>[^/]+)/(?<repo>[^/]+?)(?:(.git)?(/)?)" +
            "(?<pathRaw>(/blob/|/tree/).+)?)$",
    "git" to "(?<protocols>(?<protocol>git))://(?<domain>.+?)" +
            "(?<pathname>/(?<owner>[^/]+)/(?<repo>[^/]+?)(?:(.git)?(/)?)" +
            "(?<pathRaw>(/blob/|/tree/).+)?)$",
)
private val GITHUB_FORMATS = mapOf(
    "https" to "https://%(domain)/%(owner)/%(repo)%(dotGit)%(pathRaw)",
    "ssh" to "git@%(domain):%(owner)/%(repo)%(dotGit)%(pathRaw)",
    "git" to "git://%(domain)/%(owner)/%(repo)%(dotGit)%(pathRaw)"
)
private val GITHUB_DOMAINS = listOf("github.com", "gist.github.com")
private fun githubCleanData(_data: MutableMap<String, Any?>): MutableMap<String, Any?> {
    val data = baseCleanData(_data)
    if ((data["pathRaw"] as String).startsWith("/blob/")) {
        data["path"] = (data["pathRaw"] as String).replace("/blob/", "")
    }
    if ((data["pathRaw"] as String).startsWith("/tree/")) {
        data["branch"] = (data["pathRaw"] as String).replace("/tree/", "")
    }
    return data
}


private data class RegexContainer(val formats: Map<String, String>, val patterns: Map<String, String>,
                                  val domains: List<String>, val defaults: Map<String, String>,
                                  val cleanDataFunc: (MutableMap<String, Any?>) -> MutableMap<String, Any?> = ::baseCleanData
        ) {
    val compiledPatterns: Map<String, Regex> = patterns.map {
        it.key to Regex(it.value, RegexOption.IGNORE_CASE)
    }.toMap()
}

private val ALL_PLATFORMS = mapOf(
        "github" to RegexContainer(GITHUB_FORMATS, GITHUB_PATTERNS, GITHUB_DOMAINS, GIT_DEFAULTS, ::githubCleanData),
        "bitbucket" to RegexContainer(BITBUCKET_FORMATS, BITBUCKET_PATTERNS, BITBUCKET_DOMAINS, GIT_DEFAULTS),
        "assembla" to RegexContainer(ASSEMBLA_FORMATS, ASSEMBLA_PATTERNS, ASSEMBLA_DOMAINS, GIT_DEFAULTS),
        "base" to RegexContainer(BASE_FORMATS, BASE_PATTERNS, BASE_DOMAINS, BASE_DEFAULTS),
)


private val SUPPORTED_ATTRIBUTES = listOf(
        "domain",
        "repo",
        "owner",
        "user",
        "port",

        "url",
        "platform",
        "protocol",
        "protocols",
        "pathRaw",
)


private fun _parse(url: String): Map<String, Any?> {
    var parsed_info: MutableMap<String, Any?> = mutableMapOf<String, Any?>().withDefault{null}
    parsed_info["port"] = ""
    parsed_info["pathRaw"] = ""
    parsed_info["groupsPath"] = ""
    parsed_info["owner"] = ""

    for ((platform, container) in ALL_PLATFORMS) {
        for ((proto, regex) in container.compiledPatterns.entries) {
            val match = regex.find(url) ?: continue

            val domain = match.groups["domain"]
            if (domain != null && !container.domains.contains(domain.value)) {
                continue
            }
            SUPPORTED_ATTRIBUTES.forEach {
                try {
                    parsed_info[it] = match.groups[it]?.value ?: ""
                } catch (_: Exception) {}
            }
            container.defaults.forEach {
                parsed_info[it.key] = it.value
            }
            parsed_info = container.cleanDataFunc(parsed_info)

            parsed_info["url"] = url
            parsed_info["platform"] = platform
            parsed_info["protocol"] = proto
            return parsed_info.toMap()
        }
    }
    return parsed_info.toMap()
}

class GitUrlParsed(private val fields: Map<String, Any?>) {
    operator fun get(name: String): Any? {
        return fields[name]
    }
    fun toHttps(): String? {
        var template = ALL_PLATFORMS[this["platform"]]?.formats?.get("https")
        val items = fields.toMutableMap()
        items["portSlash"] = if (items["port"] == "") "${items["port"]}/" else ""
        items["groupsPath"] = if (items["groupsPath"] == "") "${items["groupsPath"]}/" else ""
        items["dotGit"] = if (items["repo"].toString().endsWith(".git")) "" else ".git"
        if (template != null) {
            items.entries.forEach {
                if (it.value != null) {
                    template = template!!.toString().replace("%(${it.key})", it.value.toString())
                }
            }
        }
        return template
    }
}

fun parse(url: String): GitUrlParsed {
    return GitUrlParsed(_parse(url))
}
