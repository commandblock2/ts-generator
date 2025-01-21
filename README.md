# TypeScript definition generator for graaljs from JVM types

This library generates TypeScript definitions that cover a set of Kotlin and Java classes using Kotlin reflection
specifically for the usage in graaljs.

TypeScript definitions are useful when data classes are serialized to JSON and
handled in a JavaScript or TypeScript web frontend as they enable context-aware type checking and autocompletion in a
number of IDEs and editors.

**NOTE: The generated definition is far from usable for graaljs as of now**

ts-generator supports:

* Kotlin and Java classes.
* Data classes.
* Enums.
* Any type.
* Generic classes, without type erasure.
* Generic constraints.
* Class inheritance.
* Abstract classes.
* Java beans.
* Mapping types.
* Nullability annotations, when allowed by the retention policy.
* Customizing class definitions via transformers.
* Parenthesis optimization: They are placed only when they are needed to disambiguate.
* Emitting either `null` or `undefined` for JVM nullable types.
* Functions

## No Proper Way To Install For Now

## Basic usage

The first you need is your Kotlin or Java classes or interfaces, for instance:

```kotlin
@Suppress("unused")
class ClassWithMethodsThatReturnsOrTakesFunctionalType(
    val propertyMethodReturnsLambda: () -> (() -> Int),
    val propertyMethodReturnsLambdaMightNull: () -> (() -> Int)?,
    val propertyMethodTakesLambdaMightNull: ((() -> Int)?) -> Unit,
) {
    fun regularMethod() = propertyMethodReturnsLambda
    fun regularMethodReturnsRegularMethod() =
        ClassWithMethodsThatReturnsOrTakesFunctionalType::regularMethod

    fun regularMethodThatReturnsLambdaMightNull() = null
    fun regularMethodTakesLambdaReturnsMightNull(x: () -> Int?) {}
}
```

Then use `TypeScriptGenerator` to generate the TypeScript definitions, like this:

```kotlin
TypeScriptGenerator(listOf(ClassWithMethodsThatReturnsOrTakesFunctionalType::class))
    .generateNPMPackage("test-generated-package-types")
    .writePackageTo(Path("./runs"))
```

You will get an output like this:

structure:

```tree
runs/
└── test-generated-package-types
    ├── package.json
    ├── tsconfig.json
    └── types
        ├── java
        │   └── lang
        │       ├── Any.d.ts
        │       ├── Void.d.ts
        │       └── annotation
        │           └── Annotation.d.ts
        ├── kotlin
        │   ├── Function.d.ts
        │   ├── Unit.d.ts
        │   ├── jvm
        │   │   └── functions
        │   │       └── Function0.d.ts
        │   └── reflect
        │       ├── KAnnotatedElement.d.ts
        │       ├── KCallable.d.ts
        │       ├── KClassifier.d.ts
        │       ├── KFunction.d.ts
        │       ├── KParameter.d.ts
        │       ├── KType.d.ts
        │       ├── KTypeParameter.d.ts
        │       ├── KTypeProjection.d.ts
        │       ├── KVariance.d.ts
        │       ├── KVisibility.d.ts
        │       └── Kind.d.ts
        └── me
            └── ntrrgc
                └── tsGenerator
                    └── tests
                        └── ClassWithMethodsThatReturnsOrTakesFunctionalType.d.ts

```

`package.json`

```package.json
{
    "name": "@test-generated-package-types/types",
    "version": "1.0.0",
    "private": true,
    "files": [
        "types/**/*.d.ts"
    ],
    "typesVersions": {
        "*": {
            "*": [
                "./types/*"
            ]
        }
    }
}
```

`tsconfig.json`

```tsconfig.json
{
    "compilerOptions": {
        "target": "es2018",
        "module": "commonjs",
        "declaration": true,
        "declarationMap": true,
        "baseUrl": ".",
        "paths": {
            "*": ["types/*"]
        },
        "strict": true,
        "moduleResolution": "node",
        "esModuleInterop": true,
        "skipLibCheck": false,
        "forceConsistentCasingInFileNames": true
    },
    "include": [
        "types/**/*.d.ts"
    ]
}
```

`KClassifier.d.ts`

```typescript
import type { KClassifier } from '../../kotlin/reflect/KClassifier.d.ts'
import type { Any } from '../../java/lang/Any.d.ts'
import type { KType } from '../../kotlin/reflect/KType.d.ts'
import type { KVariance } from '../../kotlin/reflect/KVariance.d.ts'
export interface KTypeParameter extends KClassifier, Any {
    isReified: boolean;
    name: string;
    upperBounds: KType[];
    variance: KVariance;
}
```

To use these definitions, you simply go to your node.js project and then copy the folder
and run `npm install file:./test-generated-package-types` and then you can start seeing type based completions.

## Advanced features

This generator can handle more complex data types. Some examples are shown below:

### Mapping types

Sometimes you want to map certain Kotlin or Java classes to native JS types, like `Date`.

This can be done with the `mappings` argument of `TypeScriptGenerator`, as show in the first example.

Note the types mapped with this feature are emitted as they were written without any further processing. This is
intended to support native JS types not defined in the Kotlin or Java backend.

### Int type But Subject to Change

Currently TypeScript only supports one number type: `number`.

This may change if [a proposal for int types](https://github.com/Microsoft/TypeScript/issues/4639) succeeds. Also, some
people may want to be extra explicit and do:

```typescript
type int = number;
```

In order to be able to document if a type may or may not be integer. In any case, you can instruct `TypeScriptGenerator`
to use explicit `int` with the `intTypeName` parameter. For instance:

```kotlin
fun main(args: Array<String>) {
    println(
        TypeScriptGenerator(
            rootClasses = setOf(
                AchievementCompletionState::class
            ),
            intTypeName = "int"
        ).definitionsText
    )
}
```

The output will be:

```typescript
interface AchievementCompletionState {
    achievementRef: string;
    reachedValue: int;
}
```

### Every thing below is currently changing

### Inheritance support

```kotlin

```

The output is:

```typescript

```

### Generics

```kotlin

```

The output is:

```typescript


```

### Maps as JS objects

```kotlin

```

The output is:

```typescript

```

### Java beans

Sometimes you want to work with long boring Java classes like this one:

```java
public class JavaClass {
    private String name;
    private int[] results;
    private boolean finished;
    private char[][] multidimensional;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int[] getResults() {
        return results;
    }

    public void setResults(int[] results) {
        this.results = results;
    }

    // setters are not required for this to work!
    public boolean isFinished() {
        return finished;
    }

    public char[][] getMultidimensional() {
        return multidimensional;
    }

    public void setMultidimensional(char[][] multidimensional) {
        this.multidimensional = multidimensional;
    }
}
```

Even though its fields are private, they are accessible through getter methods. The generator knows this, so they are
included in the definition:

```typescript
interface JavaClass {
    name: string;
    results: number[];
    multidimensional: string[][];
    finished: boolean;
}
```

### Transformers

Sometimes they objects you use in TypeScript or JavaScript are not exactly the same you use in your backend, but have
some differences, for instance:

* You may transform one type into another.
* Your classes may use camelCase in the backend but being turned into snake_case in the frontend by the JSON serializer.
* Some properties of some classes may be not be sent to the frontend.

To support cases like these, `TypeScriptGenerator` supports class transformers. They are objects implementing the
`ClassTransformer` interface, arranged in a pipeline. They can be used to customize the list of properties of a class
and their name and type.

Below are some examples:

#### Filtering unwanted properties

In the following example, assume we don't want to emit `ref`:

```kotlin
data class Achievement(
    val ref: String,
    val title: String,
    val description: String,
    val measuredProperty: (player: Player) -> Int,
    val neededValue: Int
)
```

We can use the `transformPropertyList()` to remove it.

```kotlin
fun main(args: Array<String>) {
    println(
        TypeScriptGenerator(
            rootClasses = setOf(
                Achievement::class
            ),
            classTransformers = listOf(
                object : ClassTransformer {
                    override fun transformPropertyList(
                        properties: List<KProperty<*>>,
                        klass: KClass<*>
                    ): List<KProperty<*>> {
                        return properties.filter { property ->
                            property.name != "ref"
                        }
                    }
                }
            )
        ).definitionsText)
}
```

The output is:

```typescript
interface Achievement {
    description: string;
    neededValue: number;
    title: string;
}
```

#### Renaming to snake_case

You can use `transformPropertyName()` to rename any property.

The functions `camelCaseToSnakeCase()` and `snakeCaseToCamelCase()` are included in this library.

```kotlin
data class AchievementCompletionState(
    val achievementRef: String,
    val reachedValue: Int
)

fun main(args: Array<String>) {
    println(
        TypeScriptGenerator(
            rootClasses = setOf(
                AchievementCompletionState::class
            ),
            classTransformers = listOf(
                object : ClassTransformer {
                    override fun transformPropertyName(
                        propertyName: String,
                        property: KProperty<*>,
                        klass: KClass<*>
                    ): String {
                        return camelCaseToSnakeCase(propertyName)
                    }
                }
            )
        ).definitionsText)
}
```

The output is:

```typescript
interface AchievementCompletionState {
    achievement_ref: string;
    reached_value: number;
}
```

#### Replacing types for some properties

Imagine in our previous example we don't want to emit `achievement_ref` with type `string`, but rather `achievement`,
with type `Achievement`.

We can use a combination of `transformPropertyName()` and `transformPropertyType()` for this purpose:

```typescript
fun main(args: Array<String>) {
    println(TypeScriptGenerator(
        rootClasses = setOf(
            AchievementCompletionState::class
        ),
        classTransformers = listOf(
            object : ClassTransformer {
                override fun transformPropertyName(
                    propertyName: String, 
                    property: KProperty<*>, 
                    klass: KClass<*>
                ): String {
                    if (propertyName == "achievementRef") {
                        return "achievement"
                    } else {
                        return propertyName
                    }
                }

                override fun transformPropertyType(
                    type: KType, 
                    property: KProperty<*>, 
                    klass: KClass<*>
                ): KType {
                    // Note: property is the actual property from the class
                    // (unless replaced in transformPropertyList()), so
                    // it maintains the original property name declared
                    // in the code.
                    if (property.name == "achievementRef") {
                        return Achievement::class.createType(nullable = false)
                    } else {
                        return type
                    }
                }
            }
        )
    ).definitionsText)
}
```

The output is:

```typescript
interface Achievement {
    description: string;
    neededValue: number;
    ref: string;
    title: string;
}

interface AchievementCompletionState {
    achievement: Achievement;
    reachedValue: number;
}
```

Note how `Achievement` class is emitted recursively after the transformation has taken place, even though it was not
declared in the original `AchievementCompletionState` class nor specified in `rootClasses`.

### Applying transformers only to some classes

Transformers are applied to all classes by default. If you want your transformers to apply only to classes matching a
certain predicate, you can wrap them in an instance of `FilteredClassTransformer`. This is its definition:

 ```kotlin
class FilteredClassTransformer(
    val wrappedTransformer: ClassTransformer,
    val filter: (klass: KClass<*>) -> Boolean
) : ClassTransformer
```

For the common case of applying a transformer only on a class and its subclasses if any, an extension method is
provided, `.onlyOnSubclassesOf()`:

```kotlin
fun main(args: Array<String>) {
    println(
        TypeScriptGenerator(
            rootClasses = setOf(
                Achievement::class
            ),
            classTransformers = listOf(
                object : ClassTransformer {
                    override fun transformPropertyList(
                        properties: List<KProperty<*>>,
                        klass: KClass<*>
                    ): List<KProperty<*>> {
                        return properties.filter { property ->
                            property.name != "ref"
                        }
                    }
                }.onlyOnSubclassesOf(Achievement::class)
            )
        ).definitionsText
    )
}
```

### Optional\<T\> unwrapping

This is an example of a more complex transformer that can be used to unwrap `Optional<T>` into `T | null`.

Let's suppose a Java class like this:

```java
public class JavaClassWithOptional {
    private String name;
    private String surname;

    public Optional<String> getSurname() {
        return Optional.ofNullable(surname);
    }

    public String getName() {
        return name;
    }
}
```

We could use this transformer:

```kotlin
object : ClassTransformer {
    override fun transformPropertyType(
        type: KType,
        property: KProperty<*>,
        klass: KClass<*>
    ): KType {
        val bean = Introspector.getBeanInfo(klass.java)
            .propertyDescriptors
            .find { it.name == property.name }

        val getterReturnType = bean?.readMethod?.kotlinFunction?.returnType
        if (getterReturnType?.classifier == Optional::class) {
            val wrappedType = getterReturnType.arguments.first().type!!
            return wrappedType.withNullability(true)
        } else {
            return type
        }
    }
}
```

The result would be this:

```typescript
interface JavaClassWithOptional {
    name: string;
    surname: string | null;
}
```

# License

This project is dual licensed, the part that comes from the original code base is subject to the Apache License

```text
Copyright 2017 Alicia Boya García

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

And for the later modified/added code, are licensed under GPLv3

```
Modifications Copyright 2024-2025 commandblock2
Modified portions are licensed under GPLv3:

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, version 3.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.
```