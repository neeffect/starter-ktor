package dev.neeffect.example.todo

sealed class TodoError {

    object NotFound : TodoError()
    object InvalidState : TodoError()
    object InvalidParam : TodoError()
    data class InternalError(val msg: String) : TodoError()
}
