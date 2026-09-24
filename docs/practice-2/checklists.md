## 1. Дымовое тестирование

Быстрая оценка работоспособности после сборки / docker compose up.

- [ ] docker compose up -d поднимает 11 сервисов + PostgreSQL + RabbitMQ
- [ ] Контейнеры в статусе Up
- [ ] Health-check сервисов возвращает HTTP 200 и статус ok
- [ ] POST /api/cards/generate через Gateway создаёт карты
- [ ] GET /api/cards?limit=10 возвращает 200 и непустой список
- [ ] Смоук завершается успехом

---

## 2. Критический путь

Ключевые сценарии процессинговой цепочки: терминал -> Gateway -> Switch -> Authorization -> CMS -> Logger.

- [ ] Генерация пула карт
- [ ] Покупка по ACTIVE-карте с суммой ниже лимитов и баланса: цепочка до Authorization, статус APPROVED, responseCode=00
- [ ] После APPROVED: availableBalance уменьшен, в limit_usage учтена сумма
- [ ] Транзакция появляется в Transaction Logger / поиске GET /api/transactions/search
- [ ] Симулятор терминалов POST /api/simulator/terminal/run отправляет транзакции успешно
- [ ] Отказ: карта не найдена -> DECLINED, код 14
- [ ] Отказ: INACTIVE / BLOCKED / EXPIRED - причина и код соответствуют ТЗ Authorization
- [ ] Отказ: превышение дневного или месячного лимита -> 61
- [ ] Отказ: недостаточно средств -> 51
- [ ] Switch маршрутизирует по BIN
- [ ] Дашборд отображает факт прошедших транзакций
- [ ] Повторный health-check ядра после прогона симулятора - сервисы живы
