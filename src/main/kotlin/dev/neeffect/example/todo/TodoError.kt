package pl.setblack.nee.example.todolist

sealed class TodoError {

    object NotFound : TodoError()
    object InvalidState : TodoError()
    object InvalidParam : TodoError()
}
