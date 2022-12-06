package com.smallcloud.codify

class Settings {
    var model: String = "CONTRASTcode/3b/py"
    var api_key: String = "473iatjQaIxYtYutrEib"
    var temperature: Float = .2f

//    fun load()
//    fun save()
    companion object {
        var instance = Settings()
    }
}