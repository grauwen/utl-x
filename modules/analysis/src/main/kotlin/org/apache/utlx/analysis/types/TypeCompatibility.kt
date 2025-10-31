// modules/analysis/src/main/kotlin/org/apache/utlx/analysis/types/TypeCompatibility.kt
package org.apache.utlx.analysis.types

/**
 * Utility object for checking type compatibility
 */
object TypeCompatibility {
    /**
     * Check if source type is compatible with target type
     */
    fun isCompatible(source: TypeDefinition, target: TypeDefinition): Boolean {
        return source.isCompatibleWith(target)
    }

    /**
     * Check if a value of the source type can be assigned to the target type
     * This is similar to isCompatible but may have different semantics
     */
    fun isAssignable(target: TypeDefinition, source: TypeDefinition): Boolean {
        return when {
            // Never type is assignable to nothing (except Never)
            source is TypeDefinition.Never -> target is TypeDefinition.Never

            // Unknown is assignable to anything
            source is TypeDefinition.Unknown -> true

            // Any can accept anything
            target is TypeDefinition.Any -> true

            // Exact type match
            target == source -> true

            // Scalar type compatibility
            target is TypeDefinition.Scalar && source is TypeDefinition.Scalar -> {
                isScalarAssignable(target.kind, source.kind)
            }

            // Array type compatibility (covariant)
            target is TypeDefinition.Array && source is TypeDefinition.Array -> {
                isAssignable(target.elementType, source.elementType)
            }

            // Object type compatibility (structural)
            target is TypeDefinition.Object && source is TypeDefinition.Object -> {
                isObjectAssignable(target, source)
            }

            // Union type compatibility - source must be assignable to at least one member
            target is TypeDefinition.Union -> {
                target.types.any { isAssignable(it, source) }
            }

            // Source is union - all members must be assignable to target
            source is TypeDefinition.Union -> {
                source.types.all { isAssignable(target, it) }
            }

            else -> false
        }
    }

    /**
     * Check if scalar types are assignable
     */
    private fun isScalarAssignable(target: ScalarKind, source: ScalarKind): Boolean {
        return when {
            target == source -> true
            // Integer can be assigned to Number
            target == ScalarKind.NUMBER && source == ScalarKind.INTEGER -> true
            else -> false
        }
    }

    /**
     * Check if source object is assignable to target object (structural typing)
     */
    private fun isObjectAssignable(target: TypeDefinition.Object, source: TypeDefinition.Object): Boolean {
        // All required properties in target must exist in source with compatible types
        for (requiredProp in target.required) {
            if (!source.properties.containsKey(requiredProp)) {
                return false
            }
            val targetPropType = target.properties[requiredProp]?.type ?: return false
            val sourcePropType = source.properties[requiredProp]?.type ?: return false
            if (!isAssignable(targetPropType, sourcePropType)) {
                return false
            }
        }
        return true
    }

    /**
     * Compute the union of two types
     * Returns a type that can represent values of either type
     */
    fun union(type1: TypeDefinition, type2: TypeDefinition): TypeDefinition {
        return when {
            // Same type
            type1 == type2 -> type1

            // One is Never - other type wins
            type1 is TypeDefinition.Never -> type2
            type2 is TypeDefinition.Never -> type1

            // One is Unknown - Unknown wins (most permissive)
            type1 is TypeDefinition.Unknown || type2 is TypeDefinition.Unknown -> TypeDefinition.Unknown

            // Both are scalars
            type1 is TypeDefinition.Scalar && type2 is TypeDefinition.Scalar -> {
                scalarUnion(type1, type2)
            }

            // Both are arrays - union of element types
            type1 is TypeDefinition.Array && type2 is TypeDefinition.Array -> {
                TypeDefinition.Array(union(type1.elementType, type2.elementType))
            }

            // Both are objects - structural union
            type1 is TypeDefinition.Object && type2 is TypeDefinition.Object -> {
                objectUnion(type1, type2)
            }

            // Already a union - flatten
            type1 is TypeDefinition.Union && type2 is TypeDefinition.Union -> {
                TypeDefinition.Union((type1.types + type2.types).distinct())
            }
            type1 is TypeDefinition.Union -> {
                TypeDefinition.Union((type1.types + type2).distinct())
            }
            type2 is TypeDefinition.Union -> {
                TypeDefinition.Union((listOf(type1) + type2.types).distinct())
            }

            // Different types - create union
            else -> TypeDefinition.Union(listOf(type1, type2))
        }
    }

    /**
     * Compute the union of two scalar types
     */
    private fun scalarUnion(type1: TypeDefinition.Scalar, type2: TypeDefinition.Scalar): TypeDefinition {
        return when {
            type1.kind == type2.kind -> type1
            // Integer + Number = Number
            (type1.kind == ScalarKind.INTEGER && type2.kind == ScalarKind.NUMBER) ||
            (type1.kind == ScalarKind.NUMBER && type2.kind == ScalarKind.INTEGER) -> {
                TypeDefinition.Scalar(ScalarKind.NUMBER)
            }
            // Different scalar types - create union
            else -> TypeDefinition.Union(listOf(type1, type2))
        }
    }

    /**
     * Compute the union of two object types
     */
    private fun objectUnion(type1: TypeDefinition.Object, type2: TypeDefinition.Object): TypeDefinition {
        // Properties that exist in both - union their types
        val commonProps = (type1.properties.keys intersect type2.properties.keys).associateWith { key ->
            val prop1 = type1.properties[key]!!
            val prop2 = type2.properties[key]!!
            PropertyType(
                type = union(prop1.type, prop2.type),
                nullable = prop1.nullable || prop2.nullable
            )
        }

        // Properties only in type1
        val onlyInType1 = type1.properties.filterKeys { it !in type2.properties.keys }.mapValues { (_, prop) ->
            PropertyType(prop.type, nullable = true) // Make nullable since not in both
        }

        // Properties only in type2
        val onlyInType2 = type2.properties.filterKeys { it !in type1.properties.keys }.mapValues { (_, prop) ->
            PropertyType(prop.type, nullable = true) // Make nullable since not in both
        }

        val allProps = commonProps + onlyInType1 + onlyInType2

        // Only properties required in both are required in union
        val requiredProps = type1.required intersect type2.required

        return TypeDefinition.Object(
            properties = allProps,
            required = requiredProps
        )
    }
}
