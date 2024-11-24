/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2024 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.config.types

import net.ccbluex.liquidbounce.event.Listenable
import net.ccbluex.liquidbounce.render.engine.Color4b
import net.ccbluex.liquidbounce.utils.input.InputBind
import net.ccbluex.liquidbounce.utils.math.Easing
import net.ccbluex.liquidbounce.utils.client.toLowerCamelCase
import net.minecraft.block.Block
import net.minecraft.client.util.InputUtil
import net.minecraft.item.Item
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i

open class Configurable(
    name: String,
    value: MutableList<Value<*>> = mutableListOf(),
    valueType: ValueType = ValueType.CONFIGURABLE,

    /**
     * Signalizes that the [Configurable]'s translation key
     * should not depend on another [Configurable].
     * This means the [baseKey] will be directly used.
     *
     * The options should be used in common options, so that
     * descriptions don't have to be written twice.
     */
    independentDescription: Boolean = false
) : Value<MutableList<Value<*>>>(
    name,
    defaultValue = value,
    valueType,
    independentDescription = independentDescription
) {

    /**
     * Stores the [Configurable] in which
     * the [Configurable] is included, can be null.
     */
    var base: Configurable? = null

    /**
     * The base key used when [base] is null,
     * otherwise the [baseKey] from [base]
     * is used when its base is null and so on.
     */
    open val baseKey: String
        get() = "liquidbounce.option.${name.toLowerCamelCase()}"

    open fun initConfigurable() {
        inner.filterIsInstance<Configurable>().forEach {
            it.initConfigurable()
        }
    }

    /**
     * Walks the path of the [Configurable] and its children
     */
    fun walkKeyPath(previousBaseKey: String? = null) {
        this.key = if (previousBaseKey != null) {
            "$previousBaseKey.${name.toLowerCamelCase()}"
        } else {
            constructBaseKey()
        }

        // Update children
        for (currentValue in this.inner) {
            if (currentValue is Configurable) {
                currentValue.walkKeyPath(this.key)
            } else {
                currentValue.key = "${this.key}.value.${currentValue.name.toLowerCamelCase()}"
            }

            if (currentValue is ChoiceConfigurable<*>) {
                val currentKey = currentValue.key

                currentValue.choices.forEach { choice -> choice.walkKeyPath(currentKey) }
            }
        }
    }

    /**
     * Joins the names of all bases and this and the [baseKey] of the lowest
     * base together to create a translation base key.
     */
    private fun constructBaseKey(): String {
        val values = mutableListOf<String>()
        var current: Configurable? = this
        while (current != null) {
            val base1 = current.base
            if (base1 == null) {
                values.add(current.baseKey)
            } else {
                values.add(current.name.toLowerCamelCase())
            }
            current = base1
        }
        values.reverse()
        return values.joinToString(".")
    }

    @get:JvmName("getContainedValues")
    val containedValues: Array<Value<*>>
        get() = this.inner.toTypedArray()

    fun getContainedValuesRecursively(): Array<Value<*>> {
        val output = mutableListOf<Value<*>>()

        this.getContainedValuesRecursivelyInternal(output)

        return output.toTypedArray()
    }

    fun getContainedValuesRecursivelyInternal(output: MutableList<Value<*>>) {
        for (currentValue in this.inner) {
            if (currentValue is ToggleableConfigurable) {
                output.add(currentValue)
                currentValue.inner.filterTo(output) { it.name.equals("Enabled", true) }
            } else {
                if (currentValue is Configurable) {
                    currentValue.getContainedValuesRecursivelyInternal(output)
                } else {
                    output.add(currentValue)
                }
            }

            if (currentValue is ChoiceConfigurable<*>) {
                output.add(currentValue)

                currentValue.choices.filter { it.isActive }.forEach {
                    it.getContainedValuesRecursivelyInternal(output)
                }
            }
        }
    }

    /**
     * Restore all values to their default values
     */
    override fun restore() {
        inner.forEach(Value<*>::restore)
    }

    // Common value types

    fun <T : Configurable> tree(configurable: T): T {
        inner.add(configurable)
        configurable.base = this
        return configurable
    }

    fun <T : Configurable> treeAll(vararg configurable: T) {
        configurable.forEach(this::tree)
    }

    fun <T : Any> value(
        name: String,
        default: T,
        valueType: ValueType = ValueType.INVALID,
        listType: ListValueType = ListValueType.None
    ) = Value(name, default, valueType, listType).apply { this@Configurable.inner.add(this) }

    fun <T : Any> rangedValue(name: String, default: T, range: ClosedRange<*>, suffix: String,
                                      valueType: ValueType) =
        RangedValue(name, default, range, suffix, valueType).apply { this@Configurable.inner.add(this) }

    // Fixed data types

    fun boolean(name: String, default: Boolean) = value(name, default, ValueType.BOOLEAN)

    fun float(name: String, default: Float, range: ClosedFloatingPointRange<Float>, suffix: String = "") =
        rangedValue(name, default, range, suffix, ValueType.FLOAT)

    fun floatRange(
        name: String,
        default: ClosedFloatingPointRange<Float>,
        range: ClosedFloatingPointRange<Float>,
        suffix: String = ""
    ) = rangedValue(name, default, range, suffix, ValueType.FLOAT_RANGE)

    fun int(name: String, default: Int, range: IntRange, suffix: String = "") =
        rangedValue(name, default, range, suffix, ValueType.INT)

    fun bind(name: String, default: Int) = bind(
        name,
        InputBind(InputUtil.Type.KEYSYM, default, InputBind.BindAction.TOGGLE)
    )

    fun bind(name: String, default: InputBind) = value(name, default, ValueType.BIND)

    fun key(name: String, default: Int) = key(name, InputUtil.Type.KEYSYM.createFromCode(default))

    fun key(name: String, default: InputUtil.Key = InputUtil.UNKNOWN_KEY) =
        value(name, default, ValueType.KEY)

    fun intRange(name: String, default: IntRange, range: IntRange, suffix: String = "") =
        rangedValue(name, default, range, suffix, ValueType.INT_RANGE)

    fun text(name: String, default: String) = value(name, default, ValueType.TEXT)

    fun textArray(name: String, default: MutableList<String>) =
        value(name, default, ValueType.TEXT_ARRAY, ListValueType.String)

    fun curve(name: String, default: Easing) = enumChoice(name, default)

    fun color(name: String, default: Color4b) = value(name, default, ValueType.COLOR)

    fun block(name: String, default: Block) = value(name, default, ValueType.BLOCK)

    fun vec3i(name: String, default: Vec3i) = value(name, default, ValueType.VECTOR_I)

    fun vec3d(name: String, default: Vec3d) = value(name, default, ValueType.VECTOR_D)

    fun blocks(name: String, default: MutableSet<Block>) =
        value(name, default, ValueType.BLOCKS, ListValueType.Block)

    fun item(name: String, default: Item) = value(name, default, ValueType.ITEM)

    fun items(name: String, default: MutableList<Item>) =
        value(name, default, ValueType.ITEMS, ListValueType.Item)

    inline fun <reified T> enumChoice(name: String, default: T): ChooseListValue<T>
        where T : Enum<T>, T : NamedChoice = enumChoice(name, default, enumValues<T>())

    fun <T> enumChoice(name: String, default: T, choices: Array<T>): ChooseListValue<T>
        where T : Enum<T>, T : NamedChoice =
        ChooseListValue(name, default, choices).apply { this@Configurable.inner.add(this) }

    fun <T : Choice> choices(
        listenable: Listenable,
        name: String,
        active: T,
        choices: Array<T>
    ): ChoiceConfigurable<T> {
        return ChoiceConfigurable<T>(listenable, name, { active }) { choices }.apply {
            this@Configurable.inner.add(this)
            this.base = this@Configurable
        }
    }

    fun <T : Choice> choices(
        listenable: Listenable,
        name: String,
        activeCallback: (ChoiceConfigurable<T>) -> T,
        choicesCallback: (ChoiceConfigurable<T>) -> Array<T>
    ): ChoiceConfigurable<T> {
        return ChoiceConfigurable(listenable, name, activeCallback, choicesCallback).apply {
            this@Configurable.inner.add(this)
            this.base = this@Configurable
        }
    }

    fun value(value: Value<*>) = value.apply { this@Configurable.inner.add(this) }

}
