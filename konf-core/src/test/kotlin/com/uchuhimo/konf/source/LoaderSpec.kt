/*
 * Copyright 2017-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.uchuhimo.konf.source

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.throws
import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec
import com.uchuhimo.konf.tempFileOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.jetbrains.spek.subject.SubjectSpek
import spark.Service
import java.util.concurrent.TimeUnit

object LoaderSpec : SubjectSpek<Loader>({
    val parentConfig = Config {
        addSpec(SourceType)
    }
    subject {
        parentConfig.from.properties
    }

    given("a loader") {
        it("should fork from parent config") {
            assertThat(subject.config, equalTo(parentConfig))
        }
        on("load from reader") {
            val config = subject.reader("type = reader".reader())
            it("should return a config which contains value from reader") {
                assertThat(config[SourceType.type], equalTo("reader"))
            }
        }
        on("load from input stream") {
            val config = subject.inputStream(
                tempFileOf("type = inputStream").inputStream())
            it("should return a config which contains value from input stream") {
                assertThat(config[SourceType.type], equalTo("inputStream"))
            }
        }
        on("load from file") {
            val config = subject.file(tempFileOf("type = file"))
            it("should return a config which contains value in file") {
                assertThat(config[SourceType.type], equalTo("file"))
            }
        }
        on("load from file path") {
            val config = subject.file(tempFileOf("type = file").toString())
            it("should return a config which contains value in file") {
                assertThat(config[SourceType.type], equalTo("file"))
            }
        }
        val isMac = "mac" in System.getProperty("os.name").toLowerCase()
        on("load from watched file") {
            val file = tempFileOf("type = originalValue")
            val config = subject.watchFile(file, context = Dispatchers.Sequential)
            val originalValue = config[SourceType.type]
            file.writeText("type = newValue")
            runBlocking(Dispatchers.Sequential) {
                delay(TimeUnit.SECONDS.toMillis(if (isMac) 10 else 5))
            }
            val newValue = config[SourceType.type]
            it("should return a config which contains value in file") {
                assertThat(originalValue, equalTo("originalValue"))
            }
            it("should load new value when file has been changed") {
                assertThat(newValue, equalTo("newValue"))
            }
        }
        on("load from watched file path") {
            val file = tempFileOf("type = originalValue")
            val config = subject.watchFile(file.toString(), context = Dispatchers.Sequential)
            val originalValue = config[SourceType.type]
            file.writeText("type = newValue")
            runBlocking(Dispatchers.Sequential) {
                delay(TimeUnit.SECONDS.toMillis(if (isMac) 10 else 5))
            }
            val newValue = config[SourceType.type]
            it("should return a config which contains value in file") {
                assertThat(originalValue, equalTo("originalValue"))
            }
            it("should load new value when file has been changed") {
                assertThat(newValue, equalTo("newValue"))
            }
        }
        on("load from string") {
            val config = subject.string("type = string")
            it("should return a config which contains value in string") {
                assertThat(config[SourceType.type], equalTo("string"))
            }
        }
        on("load from byte array") {
            val config = subject.bytes("type = bytes".toByteArray())
            it("should return a config which contains value in byte array") {
                assertThat(config[SourceType.type], equalTo("bytes"))
            }
        }
        on("load from byte array slice") {
            val config = subject.bytes("|type = slice|".toByteArray(), 1, 12)
            it("should return a config which contains value in byte array slice") {
                assertThat(config[SourceType.type], equalTo("slice"))
            }
        }
        on("load from HTTP URL") {
            val service = Service.ignite()
            service.port(0)
            service.get("/source") { _, _ -> "type = http" }
            service.awaitInitialization()
            val config = subject.url("http://localhost:${service.port()}/source")
            it("should return a config which contains value in URL") {
                assertThat(config[SourceType.type], equalTo("http"))
            }
            service.stop()
        }
        on("load from file URL") {
            val file = tempFileOf("type = fileUrl")
            val config = subject.url(file.toURI().toURL())
            it("should return a config which contains value in URL") {
                assertThat(config[SourceType.type], equalTo("fileUrl"))
            }
        }
        on("load from file URL string") {
            val url = tempFileOf("type = fileUrl").toURI().toURL().toString()
            val config = subject.url(url)
            it("should return a config which contains value in URL") {
                assertThat(config[SourceType.type], equalTo("fileUrl"))
            }
        }
        on("load from watched HTTP URL") {
            var content = "type = originalValue"
            val service = Service.ignite()
            service.port(0)
            service.get("/source") { _, _ -> content }
            service.awaitInitialization()
            val url = "http://localhost:${service.port()}/source"
            val config = subject.watchUrl(url, context = Dispatchers.Sequential)
            val originalValue = config[SourceType.type]
            content = "type = newValue"
            runBlocking(Dispatchers.Sequential) {
                delay(TimeUnit.SECONDS.toMillis(5))
            }
            val newValue = config[SourceType.type]
            it("should return a config which contains value in URL") {
                assertThat(originalValue, equalTo("originalValue"))
            }
            it("should load new value after URL content has been changed") {
                assertThat(newValue, equalTo("newValue"))
            }
        }
        on("load from watched file URL") {
            val file = tempFileOf("type = originalValue")
            val config = subject.watchUrl(file.toURI().toURL(), context = Dispatchers.Sequential)
            val originalValue = config[SourceType.type]
            file.writeText("type = newValue")
            runBlocking(Dispatchers.Sequential) {
                delay(TimeUnit.SECONDS.toMillis(5))
            }
            val newValue = config[SourceType.type]
            it("should return a config which contains value in URL") {
                assertThat(originalValue, equalTo("originalValue"))
            }
            it("should load new value after URL content has been changed") {
                assertThat(newValue, equalTo("newValue"))
            }
        }
        on("load from watched file URL string") {
            val file = tempFileOf("type = originalValue")
            val url = file.toURI().toURL()
            val config = subject.watchUrl(url.toString(), context = Dispatchers.Sequential)
            val originalValue = config[SourceType.type]
            file.writeText("type = newValue")
            runBlocking(Dispatchers.Sequential) {
                delay(TimeUnit.SECONDS.toMillis(5))
            }
            val newValue = config[SourceType.type]
            it("should return a config which contains value in URL") {
                assertThat(originalValue, equalTo("originalValue"))
            }
            it("should load new value after URL content has been changed") {
                assertThat(newValue, equalTo("newValue"))
            }
        }
        on("load from resource") {
            val config = subject.resource("source/provider.properties")
            it("should return a config which contains value in resource") {
                assertThat(config[SourceType.type], equalTo("resource"))
            }
        }
        on("load from non-existed resource") {
            it("should throw SourceNotFoundException") {
                assertThat({ subject.resource("source/no-provider.properties") },
                    throws<SourceNotFoundException>())
            }
        }
    }
})

private object SourceType : ConfigSpec("") {
    val type by required<String>()
}
