## 1. Классы эквивалентности

### Authorization. Статус карты

`card_status`: ACTIVE, INACTIVE, BLOCKED, EXPIRED

| Класс | Представитель | Ожидание |
|---|---|---|
| карта есть, ACTIVE | ACTIVE | операция может пройти дальше |
| INACTIVE | INACTIVE | отказ, CARD_INACTIVE |
| BLOCKED | BLOCKED | отказ, CARD_BLOCKED |
| EXPIRED | EXPIRED | отказ, код 54 |
| карты нет | неизвестный PAN | отказ, код 14 |

### Authorization. Срок, лимиты, баланс

| Поле | Класс | Представитель | Ожидание |
|---|---|---|---|
| expiryDate | срок не истёк | 1228 | проверка срока пройдена |
| expiryDate | срок истёк | 0826 | отказ, код 54 |
| сумма vs дневной лимит | в пределах | 900.03 при лимите 1500.00 | ок |
| сумма vs дневной лимит | превышен | 1500.01 при лимите 1500.00 | отказ, код 61 |
| сумма vs месячный лимит | в пределах | 900.03 при лимите 5000.00 | ок |
| сумма vs месячный лимит | превышен | 5000.01 при лимите 5000.00 | отказ, код 61 |
| сумма vs баланс | хватает | 900.03 при балансе 1000.00 | ок |
| сумма vs баланс | не хватает | 1000.01 при балансе 1000.00 | отказ, код 51 |

### Card-Management

| Поле | Класс                                 | Представитель | Ожидание |
|---|---------------------------------------|---|---|
| BIN | 6 цифр                                | 400000 | карта создана, PAN 16 цифр |
| BIN | не 6 цифр                             | 40000 | ошибка |
| PAN | 16 цифр, Луна ок                      | сгенерированный PAN | карта найдена |
| PAN | Луна не ок                            | та же длина, битая цифра | карта не найдена |
| status | ACTIVE / INACTIVE / BLOCKED / EXPIRED | ACTIVE | в списке по фильтру |
| status | DELETED                               | после DELETE | GET не возвращает |
| count генератора | \> 0                                  | 100 | создано 100 карт |
| count генератора | 0                                     | 0 | ошибка |

---

## 2. Граничные значения

Базовая карта: активна, баланс 1000.00, дневной лимит 1500.00, месячный 5000.00, операций не было.

| Поле | Меньше | ON | Больше | Ожидание |
|---|---|---|---|---|
| дневной лимит 1500.00 | 1499.99 | 1500.00 | 1500.01 | 1499.99 и 1500.00 - успех; 1500.01 - отказ 61 |
| месячный лимит 5000.00 | 4999.99 | 5000.00 | 5000.01 | 4999.99 и 5000.00 - успех (если дневной и баланс позволяют); 5000.01 - отказ 61 |
| баланс 1000.00 | 999.99 | 1000.00 | 1000.01 | 999.99 и 1000.00 - успех; 1000.01 - отказ 51 |
| expiry, текущий месяц 0926 | 0826 | 0926 | 1026 | 0826 - отказ 54; 0926 и 1026 - срок ок |
| длина PAN | 15 цифр | 16 | 17 | только 16 - норма |
| BIN | 5 цифр | 6 | 7 | только 6 - норма |

---

## 3. Попарное тестирование

Что проверяем попарно

Попарный набор проверяет взаимодействие каждого параметра со всеми остальными по два за раз. Для Authorization это комбинации статуса карты, наличия карты, трёх видов лимитов, срока действия, типа терминала и MCC. Для Card-Management это комбинации операции, наличия карты, статуса, валидности PAN, валидности BIN, тела запроса, поля обновления и суммы против баланса.

Каждая строка набора становится отдельным тест-кейсом. В результате мы убеждаемся что ни одна пара входных условий не даёт неожиданный ответ. Например статус BLOCKED вместе с терминалом ECOM даёт именно отказ по блокировке а не ошибку терминала, а валидный PAN на несуществующей карте даёт именно 404 а не ошибку валидации Луна.

Что исключаем из попарного набора

Невозможные по логике сервиса сочетания исключаются ограничениями модели. Для Authorization это любые сочетания неактивного статуса или просроченного срока со значениями лимитов отличными от below. Проверка таких сочетаний не имеет смысла потому что сервис откажет раньше чем дойдёт до проверки лимитов.

Для Card-Management операции get delete list не работают с телом запроса и суммой резерва. Операции create и generate не принимают готовый PAN. Операция patch единственная работает с patch_field. Операция reserve единственная учитывает сумму против баланса. Все эти сочетания исключаются из генерации ограничениями модели чтобы набор не содержал бессмысленные строки.

Что остаётся за попарным покрытием

Попарный набор не проверяет взаимодействие трёх и более параметров сразу. Ручные кейсы на такие сочетания в рамках практики два не добавлены.

Некоторые пары намеренно исключены ограничениями модели и поэтому не встречаются в наборе. Для Authorization это любые сочетания неактивного статуса, отсутствия карты или просроченного срока вместе со значениями лимитов отличными от below. Для Card-Management это операции без тела запроса, поля patch не на patch операции, сумма резерва не на reserve.

### Authorization
8 параметров: card_status, card_exists, amount_vs_daily, amount_vs_monthly, amount_vs_balance, expiry, terminal_type, mcc
pairwise дал 31 строку.

### Card-Management
8 параметров: operation, card_exists, card_status, pan_validity, bin_validity, body_validity, patch_field, amount_vs_balance
pairwise дал 52 строки.
---

## 4. Тест-кейсы

### Authorization: статус карты

`card_status`: ACTIVE, INACTIVE, BLOCKED, EXPIRED

#### TC-01 (позитивный, КЭ: ACTIVE)

Требование: tz/04-authorization.md  
Источник: класс эквивалентности, статус карты ACTIVE

Предусловие:
1. Карта заведена в системе
2. На карте положительный баланс 1000.00 руб
3. Карта активна
4. По этой карте операций ещё не было
5. Дневной лимит 1500.00, месячный 5000.00

Статус карты: ACTIVE  
Сумма транзакции: 900.03 руб  
Тип терминала: POS  
MCC: grocery

Шаги:
1. Проводим транзакцию

Ожидаемый результат:
1. Операция успешна
   - вернулся код ответа 00
   - средства зарезервированы на карте
   - доступный баланс уменьшился на 900.03
   - дневной и месячный usage увеличились на 900.03

#### TC-02 (негативный, КЭ: INACTIVE)

Требование: tz/04-authorization.md  
Источник: класс эквивалентности, статус карты INACTIVE

Предусловие:
1. Карта заведена
2. Баланс 1000.00 руб
3. Статус карты: INACTIVE
4. Операций по карте не было

Сумма: 900.03 руб  
Терминал: POS

Шаги:
1. Проводим транзакцию

Ожидаемый результат:
1. Операция отклонена, причина CARD_INACTIVE
2. Баланс не изменился

#### TC-03 (негативный, КЭ: BLOCKED)

Требование: tz/04-authorization.md  
Источник: класс эквивалентности, статус карты BLOCKED

Предусловие: как TC-01, но статус BLOCKED  
Сумма: 900.03 руб

Шаги:
1. Проводим транзакцию

Ожидаемый результат:
1. Отклонена, причина CARD_BLOCKED
2. Баланс не изменился

#### TC-04 (негативный, КЭ: EXPIRED)

Требование: tz/04-authorization.md  
Источник: класс эквивалентности, статус карты EXPIRED

Предусловие: как TC-01, но статус EXPIRED  
Сумма: 900.03 руб

Шаги:
1. Проводим транзакцию

Ожидаемый результат:
1. Отклонена, код 54
2. Баланс не изменился

#### TC-05 (негативный, КЭ: карты нет)

Требование: tz/04-authorization.md  
Источник: класс эквивалентности, карта не найдена

Предусловие:
1. PAN `4000009999999999` в системе отсутствует

Сумма: 900.03 руб

Шаги:
1. Проводим транзакцию

Ожидаемый результат:
1. Отклонена, код 14

---

### Authorization: срок действия

#### TC-06 (негативный, ГЗ: expiry меньше текущего месяца)

Требование: tz/04-authorization.md  
Источник: граничное значение, expiry = 0826 (ON для прошедшего)

Предусловие:
1. Карта ACTIVE, баланс 1000.00, лимиты как в TC-01
2. expiryDate = 0826 (август 2026)

Сумма: 900.03 руб

Шаги:
1. Проводим транзакцию

Ожидаемый результат:
1. Отклонена, код 54
2. Баланс не изменился

#### TC-07 (позитивный, ГЗ: expiry = текущий месяц)

Требование: tz/04-authorization.md  
Источник: граничное значение, expiry = 0926 (ON текущий месяц)

Предусловие:
1. Карта ACTIVE, баланс 1000.00, лимиты как в TC-01
2. expiryDate = 0926

Сумма: 900.03 руб

Шаги:
1. Проводим транзакцию

Ожидаемый результат:
1. Операция успешна, код 00

#### TC-08 (позитивный, ГЗ: expiry больше текущего месяца)

Требование: tz/04-authorization.md  
Источник: граничное значение, expiry = 1026 (выше границы)

Предусловие:
1. Карта ACTIVE, баланс 1000.00, лимиты как в TC-01
2. expiryDate = 1026

Сумма: 900.03 руб

Шаги:
1. Проводим транзакцию

Ожидаемый результат:
1. Операция успешна, код 00

---

### Authorization: дневной лимит

База: карта ACTIVE, баланс 10000.00 (чтобы не упереться в баланс), дневной лимит 1500.00, месячный 5000.00, usage = 0.

#### TC-09 (позитивный, ГЗ: 1499.99)

Требование: tz/04-authorization.md  
Источник: граничное значение, сумма чуть ниже дневного лимита

Предусловие:
1. Карта ACTIVE, баланс 10000.00
2. Дневной лимит 1500.00, месячный 5000.00
3. usage по карте = 0

Сумма: 1499.99 руб

Шаги:
1. Проводим транзакцию

Ожидаемый результат:
1. Успех, код 00
2. usage за день = 1499.99

#### TC-10 (позитивный, ГЗ ON: 1500.00)

Требование: tz/04-authorization.md  
Источник: граничное значение, сумма ровно на дневном лимите

Предусловие: как TC-09

Сумма: 1500.00 руб

Шаги:
1. Проводим транзакцию

Ожидаемый результат:
1. Успех, код 00 (условие «меньше или равно»)

#### TC-11 (негативный, ГЗ: 1500.01)

Требование: tz/04-authorization.md  
Источник: граничное значение, сумма чуть выше дневного лимита

Предусловие: как TC-09

Сумма: 1500.01 руб

Шаги:
1. Проводим транзакцию

Ожидаемый результат:
1. Отказ, код 61
2. Баланс и usage не изменились

---

### Authorization: месячный лимит

База: дневной лимит 10000.00, месячный 5000.00, баланс 10000.00.

#### TC-12 (позитивный, ГЗ ON: 5000.00)

Требование: tz/04-authorization.md
Источник: граничное значение, сумма ровно на месячном лимите

Предусловие:
1. Карта ACTIVE
2. Дневной лимит 10000.00, месячный 5000.00
3. Баланс 10000.00, usage = 0

Сумма: 5000.00 руб

Шаги:
1. Проводим транзакцию

Ожидаемый результат:
1. Успех, код 00

#### TC-13 (негативный, ГЗ: 5000.01)

Требование: tz/04-authorization.md
Источник: граничное значение, сумма чуть выше месячного лимита

Предусловие: как TC-12

Сумма: 5000.01 руб

Шаги:
1. Проводим транзакцию

Ожидаемый результат:
1. Отказ, код 61

---

### Authorization: баланс

База: карта ACTIVE, баланс 1000.00, дневной 1500.00, месячный 5000.00.

#### TC-14 (позитивный, ГЗ: 999.99)

Требование: tz/04-authorization.md  
Источник: граничное значение, сумма чуть ниже баланса

Предусловие:
1. Карта ACTIVE, баланс 1000.00
2. Дневной лимит 1500.00, месячный 5000.00

Сумма: 999.99 руб

Шаги:
1. Проводим транзакцию

Ожидаемый результат:
1. Успех, код 00
2. Баланс стал 0.01

#### TC-15 (позитивный, ГЗ ON: 1000.00)

Требование: tz/04-authorization.md  
Источник: граничное значение, сумма ровно на балансе

Предусловие: как TC-14

Сумма: 1000.00 руб

Шаги:
1. Проводим транзакцию

Ожидаемый результат:
1. Успех, код 00
2. Баланс стал 0

#### TC-16 (негативный, ГЗ: 1000.01)

Требование: tz/04-authorization.md  
Источник: граничное значение, сумма чуть выше баланса

Предусловие: как TC-14

Сумма: 1000.01 руб

Шаги:
1. Проводим транзакцию

Ожидаемый результат:
1. Отказ, код 51
2. Баланс остался 1000.00

---

### Authorization: CMS недоступен

#### TC-17 (негативный, КЭ)

Требование: tz/04-authorization.md  
Источник: класс эквивалентности, CMS выключен

Предусловие:
1. Authorization запущен
2. Card Management выключен

Шаги:
1. Проводим транзакцию на 900.03 руб

Ожидаемый результат:
1. Отклонена, код 05, причина ISSUER_TIMEOUT

---

### Card-Management

#### TC-18 (позитивный, создание карты)

Требование: tz/05-card-management.md
Источник: класс эквивалентности, валидный BIN 6 цифр

Предусловие:
1. CMS доступен

Шаги:
1. Создаём карту: BIN 400000, имя IVAN IVANOV, валюта 643, дневной лимит 1500.00, месячный 5000.00, баланс 1000.00

Ожидаемый результат:
1. Карта создана, статус ACTIVE
2. PAN из 16 цифр, начинается с 400000, проходит Луна
3. expiryDate = текущий месяц + 3 года

#### TC-19 (негативный, BIN из 5 цифр)

Требование: tz/05-card-management.md  
Источник: класс эквивалентности, невалидный BIN

Предусловие:
1. CMS доступен

Шаги:
1. Создание карты с BIN `40000`

Ожидаемый результат:
1. Ошибка
2. Карта не создана

#### TC-20 (позитивный, мягкое удаление)

Требование: tz/05-card-management.md  
Источник: класс эквивалентности, статус DELETED

Предусловие:
1. Карта ACTIVE есть в системе

Шаги:
1. DELETE карты
2. GET по PAN
3. GET списка карт

Ожидаемый результат:
1. GET по PAN - не найдена
2. В общем списке карты нет

#### TC-21 (позитивный, генератор)

Требование: tz/05-card-management.md  
Источник: класс эквивалентности, count генератора > 0

Предусловие:
1. CMS доступен
2. В БД нет карт с BIN 400000–400004

Шаги:
1. generate, count = 100, BIN 400000–400004

Ожидаемый результат:
1. Создано 100 карт
2. Статусы примерно 95% ACTIVE / 3% INACTIVE / 2% BLOCKED
3. месячный лимит = дневной × 30

#### TC-22 (позитивный, reserve)

Требование: tz/05-card-management.md  
Источник: класс эквивалентности, резервирование средств

Предусловие:
1. Карта ACTIVE, баланс 1000.00

Шаги:
1. reserve на 150.00, rrn из 12 цифр

Ожидаемый результат:
1. Баланс стал 850.00

---

### Попарный набор Authorization (PICT)

Общие шаги: готовим карту как в строке → проводим транзакцию.

Сводная таблица набора:

| ID | Строка (card_status, exists, daily, monthly, balance, expiry, terminal, mcc) | Ожидание |
|---|---|---|
| TC-PW-01 | EXPIRED, yes, below, below, below, valid, POS, restaurant | отказ 54 (статус EXPIRED) |
| TC-PW-02 | INACTIVE, yes, below, below, below, current_month, ECOM, restaurant | отказ CARD_INACTIVE |
| TC-PW-03 | EXPIRED, yes, below, below, below, current_month, ATM, travel | отказ 54 (статус EXPIRED) |
| TC-PW-04 | INACTIVE, yes, below, below, below, expired, POS, electronics | отказ CARD_INACTIVE |
| TC-PW-05 | EXPIRED, yes, below, below, below, expired, ECOM, grocery | отказ 54 (статус EXPIRED) |
| TC-PW-06 | BLOCKED, yes, below, below, below, expired, ATM, restaurant | отказ CARD_BLOCKED |
| TC-PW-07 | ACTIVE, yes, equal, equal, equal, valid, POS, travel | успех 00, баланс 0 |
| TC-PW-08 | BLOCKED, yes, below, below, below, current_month, POS, grocery | отказ CARD_BLOCKED |
| TC-PW-09 | BLOCKED, yes, below, below, below, valid, ECOM, electronics | отказ CARD_BLOCKED |
| TC-PW-10 | ACTIVE, yes, above, below, above, valid, ATM, grocery | отказ 61 (дневной лимит above) |
| TC-PW-11 | ACTIVE, yes, equal, equal, above, current_month, ECOM, grocery | отказ 51 (баланс above) |
| TC-PW-12 | ACTIVE, yes, equal, above, equal, current_month, ATM, electronics | отказ 61 (месячный лимит above) |
| TC-PW-13 | ACTIVE, yes, above, above, equal, current_month, ECOM, restaurant | отказ 61 (лимиты above) |
| TC-PW-14 | ACTIVE, yes, below, equal, above, valid, ATM, electronics | отказ 51 (баланс above) |
| TC-PW-15 | EXPIRED, yes, below, below, below, valid, POS, electronics | отказ 54 (статус EXPIRED) |
| TC-PW-16 | ACTIVE, yes, below, below, equal, valid, POS, grocery | успех 00, баланс 0 |
| TC-PW-17 | ACTIVE, no, below, below, below, valid, POS, electronics | отказ 14 (карты нет) |
| TC-PW-18 | INACTIVE, yes, below, below, below, valid, ATM, travel | отказ CARD_INACTIVE |
| TC-PW-19 | ACTIVE, yes, equal, above, above, valid, POS, restaurant | отказ 61 (месячный above) |
| TC-PW-20 | ACTIVE, yes, above, equal, below, current_month, ATM, restaurant | отказ 61 (дневной above) |
| TC-PW-21 | ACTIVE, no, below, below, below, valid, ATM, restaurant | отказ 14 (карты нет) |
| TC-PW-22 | BLOCKED, yes, below, below, below, expired, ECOM, travel | отказ CARD_BLOCKED |
| TC-PW-23 | ACTIVE, no, below, below, below, valid, ECOM, travel | отказ 14 (карты нет) |
| TC-PW-24 | ACTIVE, yes, above, above, below, current_month, POS, travel | отказ 61 (лимиты above) |
| TC-PW-25 | ACTIVE, yes, equal, below, below, current_month, ECOM, grocery | успех 00 (equal daily, below monthly/balance) |
| TC-PW-26 | ACTIVE, yes, below, below, below, expired, POS, grocery | отказ 54 (expiry expired) |
| TC-PW-27 | ACTIVE, no, below, below, below, valid, ATM, grocery | отказ 14 (карты нет) |
| TC-PW-28 | ACTIVE, yes, above, above, above, valid, ECOM, electronics | отказ 61 (лимиты above) |
| TC-PW-29 | ACTIVE, yes, below, above, above, valid, ATM, travel | отказ 61 (месячный above) |
| TC-PW-30 | INACTIVE, yes, below, below, below, expired, ECOM, grocery | отказ CARD_INACTIVE |
| TC-PW-31 | ACTIVE, yes, equal, above, below, valid, POS, grocery | отказ 61 (месячный above) |

Пример развёртки двух строк:

#### TC-PW-07 (позитивный, PICT строка 8)

Требование: tz/04-authorization.md  
Источник: PICT, cases.txt строка 8 (ACTIVE, yes, equal, equal, equal, valid, POS, travel)

Предусловие:
1. Карта заведена, статус ACTIVE
2. expiryDate = valid (больше текущего месяца)
3. Операций не было
4. Сумма транзакции равна дневному лимиту, месячному остатку и балансу (например все 1000.00)
5. CMS доступен

Терминал: POS
MCC: travel

Шаги:
1. Проводим транзакцию

Ожидаемый результат:
1. Операция успешна, код 00
2. Средства зарезервированы, баланс 0

---

#### TC-PW-09 (негативный, PICT строка 10)

Требование: tz/04-authorization.md  
Источник: PICT, cases.txt строка 10 (BLOCKED, yes, below, below, below, valid, ECOM, electronics)

Предусловие:
1. Карта заведена, статус BLOCKED
2. expiryDate = valid (больше текущего месяца)
3. Операций не было
4. Лимиты и баланс достаточные для below
5. CMS доступен

Терминал: ECOM
MCC: electronics
Сумма: 900.03 руб

Шаги:
1. Проводим транзакцию

Ожидаемый результат:
1. Операция отклонена, причина CARD_BLOCKED
2. Баланс и лимиты не изменились

---

### Попарный набор Card-Management

Общие шаги: выставить карту/тело как в строке → вызвать operation.

| ID | Строка (operation, exists, status, pan, bin, body, patch, amount) | Ожидание |
|---|---|---|
| TC-CMS-PW-01 | reserve, yes, INACTIVE, valid_luhn, valid_6, valid, status, below | 200, баланс уменьшен |
| TC-CMS-PW-02 | patch, no, DELETED, valid_luhn, valid_6, valid, monthly_limit, below | 404 |
| TC-CMS-PW-03 | generate, no, ACTIVE, valid_luhn, valid_6, empty_required, status, below | ошибка: нет count/bins |
| TC-CMS-PW-04 | reserve, yes, ACTIVE, valid_luhn, valid_6, negative_number, status, above | ошибка тела / отказ по сумме |
| TC-CMS-PW-05 | reserve, no, DELETED, valid_luhn, valid_6, valid, status, below | 404 |
| TC-CMS-PW-06 | list, yes, INACTIVE, valid_luhn, invalid, valid, status, below | ошибка фильтра или пустой список |
| TC-CMS-PW-07 | get, yes, BLOCKED, invalid_luhn, valid_6, valid, status, below | 404 |
| TC-CMS-PW-08 | get, yes, ACTIVE, wrong_length, valid_6, valid, status, below | 400/404 |
| TC-CMS-PW-09 | list, yes, EXPIRED, valid_luhn, valid_6, valid, status, below | 200, в выдаче нет DELETED |
| TC-CMS-PW-10 | delete, yes, EXPIRED, invalid_luhn, valid_6, valid, status, below | 404 |
| TC-CMS-PW-11 | patch, yes, EXPIRED, valid_luhn, valid_6, negative_number, daily_limit, below | ошибка валидации |
| TC-CMS-PW-12 | get, no, DELETED, valid_luhn, valid_6, valid, status, below | 404 |
| TC-CMS-PW-13 | patch, yes, ACTIVE, valid_luhn, valid_6, empty_required, available_balance, below | 400, пустое тело |
| TC-CMS-PW-14 | patch, yes, BLOCKED, valid_luhn, valid_6, negative_number, available_balance, below | ошибка валидации |
| TC-CMS-PW-15 | delete, no, DELETED, valid_luhn, valid_6, valid, status, below | 404 |
| TC-CMS-PW-16 | list, yes, BLOCKED, valid_luhn, invalid, valid, status, below | ошибка фильтра или пустой список |
| TC-CMS-PW-17 | patch, yes, EXPIRED, valid_luhn, valid_6, empty_required, available_balance, below | 400 |
| TC-CMS-PW-18 | patch, yes, INACTIVE, valid_luhn, valid_6, negative_number, monthly_limit, below | ошибка валидации |
| TC-CMS-PW-19 | get, yes, EXPIRED, wrong_length, valid_6, valid, status, below | 400/404 |
| TC-CMS-PW-20 | get, yes, INACTIVE, wrong_length, valid_6, valid, status, below | 400/404 |
| TC-CMS-PW-21 | patch, yes, BLOCKED, valid_luhn, valid_6, empty_required, monthly_limit, below | 400 |
| TC-CMS-PW-22 | list, yes, ACTIVE, valid_luhn, invalid, valid, status, below | ошибка фильтра или пустой список |
| TC-CMS-PW-23 | patch, no, DELETED, valid_luhn, valid_6, valid, available_balance, below | 404 |
| TC-CMS-PW-24 | delete, yes, BLOCKED, wrong_length, valid_6, valid, status, below | 400/404 |
| TC-CMS-PW-25 | patch, yes, EXPIRED, valid_luhn, valid_6, negative_number, monthly_limit, below | ошибка валидации |
| TC-CMS-PW-26 | reserve, yes, INACTIVE, valid_luhn, valid_6, empty_required, status, equal | 400, нет amount |
| TC-CMS-PW-27 | patch, no, DELETED, valid_luhn, valid_6, valid, daily_limit, below | 404 |
| TC-CMS-PW-28 | patch, yes, INACTIVE, invalid_luhn, valid_6, valid, available_balance, below | 404 |
| TC-CMS-PW-29 | reserve, yes, BLOCKED, valid_luhn, valid_6, valid, status, above | отказ: сумма > баланса |
| TC-CMS-PW-30 | patch, yes, ACTIVE, wrong_length, valid_6, valid, available_balance, below | 400/404 |
| TC-CMS-PW-31 | create, no, ACTIVE, valid_luhn, valid_6, negative_number, status, below | ошибка: отрицательный лимит/баланс |
| TC-CMS-PW-32 | reserve, yes, EXPIRED, valid_luhn, valid_6, valid, status, equal | 200, баланс 0 |
| TC-CMS-PW-33 | create, no, ACTIVE, valid_luhn, invalid, valid, status, below | ошибка валидации BIN |
| TC-CMS-PW-34 | patch, yes, ACTIVE, invalid_luhn, valid_6, valid, monthly_limit, below | 404 |
| TC-CMS-PW-35 | reserve, yes, EXPIRED, valid_luhn, valid_6, empty_required, status, above | 400, нет amount |
| TC-CMS-PW-36 | patch, yes, INACTIVE, valid_luhn, valid_6, empty_required, daily_limit, below | 400 |
| TC-CMS-PW-37 | create, no, ACTIVE, valid_luhn, valid_6, empty_required, status, below | ошибка: нет обязательных полей |
| TC-CMS-PW-38 | reserve, yes, ACTIVE, valid_luhn, valid_6, negative_number, status, equal | ошибка: отрицательная сумма |
| TC-CMS-PW-39 | patch, yes, ACTIVE, wrong_length, valid_6, valid, monthly_limit, below | 400/404 |
| TC-CMS-PW-40 | delete, yes, INACTIVE, invalid_luhn, valid_6, valid, status, below | 404 |
| TC-CMS-PW-41 | list, yes, DELETED, valid_luhn, invalid, valid, status, below | DELETED в списке нет |
| TC-CMS-PW-42 | patch, yes, ACTIVE, wrong_length, valid_6, valid, daily_limit, below | 400/404 |
| TC-CMS-PW-43 | reserve, yes, EXPIRED, wrong_length, valid_6, valid, status, below | 400/404 |
| TC-CMS-PW-44 | reserve, yes, INACTIVE, valid_luhn, valid_6, valid, status, above | отказ: сумма > баланса |
| TC-CMS-PW-45 | generate, no, ACTIVE, valid_luhn, valid_6, negative_number, status, below | ошибка: count < 1 |
| TC-CMS-PW-46 | generate, no, ACTIVE, valid_luhn, invalid, valid, status, below | ошибка валидации BIN |
| TC-CMS-PW-47 | patch, yes, BLOCKED, invalid_luhn, valid_6, valid, daily_limit, below | 404 |
| TC-CMS-PW-48 | reserve, yes, BLOCKED, valid_luhn, valid_6, valid, status, equal | 200, баланс 0 |
| TC-CMS-PW-49 | reserve, yes, BLOCKED, invalid_luhn, valid_6, valid, status, below | 404 |
| TC-CMS-PW-50 | delete, yes, ACTIVE, invalid_luhn, valid_6, valid, status, below | 404 |
| TC-CMS-PW-51 | patch, yes, EXPIRED, valid_luhn, valid_6, negative_number, status, below | ошибка валидации |
| TC-CMS-PW-52 | list, yes, EXPIRED, valid_luhn, invalid, valid, status, below | ошибка фильтра или пустой список |

Пример развёртки:

#### TC-CMS-PW-33 (негативный, PICT строка 32)

Требование: tz/05-card-management.md  
Источник: PICT, card-management/cases.txt - create, BIN invalid

Предусловие:
1. CMS доступен

Шаги:
1. POST /api/cards с BIN `40000`, имя IVAN IVANOV, валюта 643, лимиты и баланс валидные

Ожидаемый результат:
1. Ошибка валидации
2. Карта не создана

#### TC-CMS-PW-01 (позитивный, PICT строка 1)

Требование: tz/05-card-management.md  
Источник: PICT - reserve, карта INACTIVE, сумма ниже баланса

Предусловие:
1. Карта заведена, статус INACTIVE
2. Баланс 1000.00
3. PAN проходит Луна

Шаги:
1. POST /api/cards/{pan}/reserve, amount = 150.00, rrn из 12 цифр

Ожидаемый результат:
1. 200 (ТЗ reserve не проверяет статус)
2. Баланс 850.00

