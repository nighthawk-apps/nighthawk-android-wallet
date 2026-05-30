import kotlinx.collections.immutable.toPersistentList

fun main() {
    val items = listOf("1", "2", "3", "4").toPersistentList()
    val sub = items.subList(0, 2)
    println(sub.javaClass.name)
}
