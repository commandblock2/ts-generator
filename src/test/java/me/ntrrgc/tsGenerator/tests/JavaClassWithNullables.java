/*
 * Copyright 2017 Alicia Boya García
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.ntrrgc.tsGenerator.tests;


import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;


public class JavaClassWithNullables {
    @NotNull
    private String name;

    @NotNull
    private int[] results;

    @Nullable
    private int[] nextResults;

    JavaClassWithNullables(@NotNull String name, @NotNull int[] results, @Nullable int[] nextResults) {
        this.name = name;
        this.results = results;
        this.nextResults = nextResults;
    }

    @NotNull
    public String getName() {
        return name;
    }

    public void setName(@NotNull String name) {
        this.name = name;
    }

    @NotNull
    public int[] getResults() {
        return results;
    }

    public void setResults(@NotNull int[] results) {
        this.results = results;
    }

    @Nullable
    public int[] getNextResults() {
        return nextResults;
    }

    public void setNextResults(@Nullable int[] nextResults) {
        this.nextResults = nextResults;
    }
}
