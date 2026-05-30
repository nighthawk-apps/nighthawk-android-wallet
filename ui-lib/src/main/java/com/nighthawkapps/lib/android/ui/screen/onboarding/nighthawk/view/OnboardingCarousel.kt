@file:Suppress("LongMethod", "MatchingDeclarationName", "MagicNumber")

package com.nighthawkapps.lib.android.ui.screen.onboarding.nighthawk.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.design.component.BodyMedium
import com.nighthawkapps.lib.android.ui.design.component.BodySmall
import com.nighthawkapps.lib.android.ui.design.component.PrimaryButton
import com.nighthawkapps.lib.android.ui.design.component.TertiaryButton
import com.nighthawkapps.lib.android.ui.design.component.TitleLarge
import kotlinx.coroutines.launch

data class OnboardingPage(
    val title: String,
    val description: String,
    val isFirstSlide: Boolean = false
)

@Composable
fun OnboardingCarousel(onComplete: () -> Unit) {
    val pages =
        listOf(
            OnboardingPage(
                title = stringResource(id = R.string.ns_onboarding_welcome),
                description = stringResource(id = R.string.onboarding_1_body),
                isFirstSlide = true
            ),
            OnboardingPage(
                title = stringResource(id = R.string.ns_onboarding_wallet_features),
                description = stringResource(id = R.string.onboarding_2_body),
                isFirstSlide = false
            ),
            OnboardingPage(
                title = stringResource(id = R.string.ns_onboarding_chat_anonymity),
                description = stringResource(id = R.string.onboarding_3_body),
                isFirstSlide = false
            )
        )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            OnboardingPageContent(page = pages[page])
        }

        // Page Indicators
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(pages.size) { iteration ->
                val color =
                    if (pagerState.currentPage == iteration) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    }
                Surface(
                    modifier =
                        Modifier
                            .padding(4.dp)
                            .size(8.dp),
                    shape = CircleShape,
                    color = color
                ) {}
            }
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = dimensionResource(id = R.dimen.screen_bottom_margin)),
            horizontalArrangement = Arrangement.Center
        ) {
            if (pagerState.currentPage < pages.size - 1) {
                TertiaryButton(
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    },
                    text = stringResource(id = R.string.ns_next),
                    modifier = Modifier.padding(end = 16.dp)
                )
            } else {
                PrimaryButton(
                    onClick = onComplete,
                    text = stringResource(id = R.string.ns_get_started),
                    modifier = Modifier.width(dimensionResource(id = R.dimen.restore_button_min_width))
                )
            }
        }
    }
}

@Composable
fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.weight(0.2f))

        Image(
            painter = painterResource(id = R.drawable.ic_nighthawk_logo),
            contentDescription = null,
            contentScale = ContentScale.Inside,
            modifier = Modifier.size(150.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
        TitleLarge(
            text = page.title,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        BodyMedium(
            text = page.description,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 32.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_darkfi_logo_white),
                contentDescription = null,
                contentScale = ContentScale.Inside,
                modifier = Modifier.size(40.dp)
            )
            if (page.isFirstSlide) {
                Spacer(modifier = Modifier.height(8.dp))
                BodySmall(
                    text = stringResource(id = R.string.ns_running_on_darkfi),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
