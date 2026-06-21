package com.lyrictica.game.reversebeat

import korlibs.image.color.Colors
import korlibs.korge.KorgeConfig
import korlibs.korge.view.addUpdater
import korlibs.korge.view.circle
import korlibs.korge.view.container
import korlibs.korge.view.solidRect
import korlibs.math.geom.Size
import korlibs.math.geom.degrees
import kotlin.random.Random

internal fun createReverseBeatKorgeConfig(runtime: ReverseBeatGameRuntime): KorgeConfig = KorgeConfig(
    virtualSize = Size(ReverseBeatGameRuntime.WORLD_WIDTH, ReverseBeatGameRuntime.WORLD_HEIGHT),
    windowSize = Size(ReverseBeatGameRuntime.WORLD_WIDTH, ReverseBeatGameRuntime.WORLD_HEIGHT),
    backgroundColor = Colors["#050814"],
    forceRenderEveryFrame = true,
    main = {
        val bgContainer = container {
            alpha = 0.8
        }
        
        // Ethereal gradient back-layer glows
        val topGlow = bgContainer.circle(420.0, Colors["#7C3AED"]).apply {
            x = 80.0; y = 120.0; alpha = 0.08
        }
        val centerGlow = bgContainer.circle(500.0, Colors["#E11D48"]).apply {
            x = ReverseBeatGameRuntime.WORLD_WIDTH * 0.5 - 250.0
            y = ReverseBeatGameRuntime.WORLD_HEIGHT * 0.5 - 250.0
            alpha = 0.06
        }
        val lowerGlow = bgContainer.circle(480.0, Colors["#0EA5E9"]).apply {
            x = 560.0; y = 1440.0; alpha = 0.08
        }

        // Abstract rotating rings
        val rings = List(3) { index ->
            val ringRadius = 250.0 + (index * 150.0)
            bgContainer.circle(ringRadius, Colors[if (index % 2 == 0) "#F43F5E" else "#38BDF8"]).apply {
                x = ReverseBeatGameRuntime.WORLD_WIDTH * 0.5 - ringRadius
                y = ReverseBeatGameRuntime.WORLD_HEIGHT * 0.5 - ringRadius
                alpha = 0.02
                // We fake a hollow ring with a slightly smaller black circle inside (or just use it as a glow)
            }
        }

        // Animated particles
        class Particle {
            var x = Random.nextDouble(ReverseBeatGameRuntime.WORLD_WIDTH)
            var y = Random.nextDouble(ReverseBeatGameRuntime.WORLD_HEIGHT)
            var speedY = Random.nextDouble(-100.0, -30.0)
            var size = Random.nextDouble(3.0, 12.0)
            val rect = solidRect(size, size, Colors[if (Random.nextBoolean()) "#67E8F9" else "#A855F7"]).apply {
                alpha = 0.0
            }
            
            fun update(dt: Double, swipeIntensity: Double) {
                y += speedY * dt * (1.0 + swipeIntensity * 4.0)
                if (y < -50.0) {
                    y = ReverseBeatGameRuntime.WORLD_HEIGHT + 50.0
                    x = Random.nextDouble(ReverseBeatGameRuntime.WORLD_WIDTH)
                }
                rect.x = x
                rect.y = y
                // Fade out near top
                val targetAlpha = if (y < 200.0) (y / 200.0) * 0.4 else 0.4
                rect.alpha = targetAlpha + (swipeIntensity * 0.3)
            }
        }
        val particles = List(40) { Particle() }

        // Center lane beam
        val centerBeam = solidRect(ReverseBeatGameRuntime.WORLD_WIDTH * 0.80, ReverseBeatGameRuntime.WORLD_HEIGHT, Colors["#060B18"]).apply {
            x = ReverseBeatGameRuntime.WORLD_WIDTH * 0.10
            y = 0.0
            alpha = 0.85
        }

        val laneFractions = listOf(0.16, 0.32, 0.50, 0.68, 0.84)
        val laneLines = laneFractions.mapIndexed { index, fraction ->
            solidRect(3.0, ReverseBeatGameRuntime.WORLD_HEIGHT, Colors[if (index % 2 == 0) "#38BDF8" else "#D946EF"]).apply {
                x = (ReverseBeatGameRuntime.WORLD_WIDTH * fraction)
                y = 0.0
                alpha = 0.08
            }
        }

        val swipeHalo = circle(260.0, Colors["#22D3EE"]).apply {
            x = ReverseBeatGameRuntime.WORLD_WIDTH * 0.5 - 260.0
            y = ReverseBeatGameRuntime.WORLD_HEIGHT * 0.74 - 260.0
            alpha = 0.0
        }

        var time = 0.0

        addUpdater {
            val dt = 1.0 / 60.0
            time += dt
            runtime.advance(dt)
            val beat = runtime.beatPulse.toDouble()
            val swipe = runtime.swipePulse.toDouble()
            val currentPhase = runtime.uiState.value.phase

            // Dynamic Theme Colors and Error Tint
            val redTint = runtime.errorTintPulse.toDouble()

            fun rgbaFromAndroidColor(color: Int): korlibs.image.color.RGBA {
                val alpha = (color ushr 24) and 0xFF
                val red = (color ushr 16) and 0xFF
                val green = (color ushr 8) and 0xFF
                val blue = color and 0xFF
                return korlibs.image.color.RGBA(red, green, blue, alpha)
            }

            fun mixColor(c1: korlibs.image.color.RGBA, c2: korlibs.image.color.RGBA, ratio: Double): korlibs.image.color.RGBA {
                val r = (c1.r + (c2.r - c1.r) * ratio).toInt().coerceIn(0, 255)
                val g = (c1.g + (c2.g - c1.g) * ratio).toInt().coerceIn(0, 255)
                val b = (c1.b + (c2.b - c1.b) * ratio).toInt().coerceIn(0, 255)
                val a = (c1.a + (c2.a - c1.a) * ratio).toInt().coerceIn(0, 255)
                return korlibs.image.color.RGBA(r, g, b, a)
            }

            val baseTop = rgbaFromAndroidColor(runtime.themeTopColor)
            val baseCenter = rgbaFromAndroidColor(runtime.themeCenterColor)
            val baseBottom = rgbaFromAndroidColor(runtime.themeBottomColor)

            topGlow.color = mixColor(baseTop, Colors.RED, redTint)
            centerGlow.color = mixColor(baseCenter, Colors.RED, redTint)
            lowerGlow.color = mixColor(baseBottom, Colors.RED, redTint)

            // Update glows
            topGlow.alpha = 0.06 + (beat * 0.10) + (swipe * 0.04)
            centerGlow.alpha = 0.04 + (beat * 0.15)
            lowerGlow.alpha = 0.06 + (beat * 0.12)
            
            val baseScale = 1.0 + Math.sin(time * 0.5) * 0.02
            topGlow.scale = baseScale + (beat * 0.20)
            centerGlow.scale = baseScale + (beat * 0.18)
            lowerGlow.scale = baseScale + (beat * 0.22)

            // Update Rings
            rings.forEachIndexed { index, ring ->
                val rotationSpeed = if (index % 2 == 0) 30.0 else -30.0
                ring.rotation = (time * rotationSpeed).degrees
                ring.alpha = if (currentPhase == ReverseBeatPhase.PLAYING) {
                    0.03 + (beat * 0.08) + (swipe * 0.06)
                } else 0.02
                ring.scale = 1.0 + (beat * (0.05 * (index + 1)))
            }

            // Update Particles
            val activeSwipe = if (currentPhase == ReverseBeatPhase.PLAYING) swipe else 0.0
            particles.forEach { it.update(dt, activeSwipe) }

            // Lane Lines and Beams
            centerBeam.alpha = 0.70 + (beat * 0.15)
            swipeHalo.alpha = swipe * 0.25
            swipeHalo.scale = 0.8 + (swipe * 0.6)

            laneLines.forEachIndexed { index, line ->
                line.alpha = if (currentPhase == ReverseBeatPhase.PLAYING) {
                    0.10 + (beat * 0.15) + (swipe * 0.08) + (Math.sin(time * 2.0 + index) * 0.05)
                } else {
                    0.08
                }
            }
        }
    }
)
