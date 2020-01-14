package  tdv.dg.boostertrackerservice
// COMMON INTERFACES

/*
 [МОДЕЛЬ БУСТЕРА]
 Следует заметить, что `duration` может отсуствовать, т.к. не все бустеры имеют длителность.
 Таким образом:
    1. Невозобновлямые бустеры — не имеют `duration`.
    2. Возобновляемые бустеры со временем — имеют `duration`.
    3. Возобновляемые бустеры без времени — тут не будут храниться, т.к. применяются моментально.
 */
public interface Trackable {
    val productId: String
    val duration: Int?
}

/*
 [СЛУШАТЕЛЬ]
 Получает уведомления об изменении времени действия бустеров каждую секунду.
 */
public interface BoosterListener {
    fun onTimeUpdate(booster: String, timeLeft: Int)
    fun onTimeEnd(booster: String)
}

// MAIN INTERFACE

/*
 [ТРЕКЕР БУСТЕРОВ]
 Занимается просчетом времени для каждого из бустера.
 
 Важно: бустеры могут добавляться с одним `Trackable.productId`, таким образом просто
 увеличивается время действия продукта.
 */
public interface BoosterTracker {
    // Начинает хранение и отслеживание бустера
    fun track(booster: Trackable)
    // Коллекция `id` всех действующих бустеров
    fun activeBoosterIds(): Set<String>
    
    fun add(listener: BoosterListener)
    fun remove(listener: BoosterListener)
}
