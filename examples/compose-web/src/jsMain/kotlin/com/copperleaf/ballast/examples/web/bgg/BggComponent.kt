package com.copperleaf.ballast.examples.web.bgg

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.copperleaf.ballast.examples.bgg.models.HotListType
import com.copperleaf.ballast.examples.bgg.ui.BggContract
import com.copperleaf.ballast.examples.util.ExamplesContext
import com.copperleaf.ballast.examples.web.util.Component
import com.copperleaf.ballast.examples.web.util.ComposeWebInjector
import com.copperleaf.ballast.examples.web.util.bulma.BulmaButton
import com.copperleaf.ballast.examples.web.util.bulma.BulmaCheckbox
import com.copperleaf.ballast.examples.web.util.bulma.BulmaPanel
import com.copperleaf.ballast.examples.web.util.bulma.BulmaProgress
import com.copperleaf.ballast.examples.web.util.bulma.BulmaSelect
import com.copperleaf.ballast.repository.cache.Cached
import com.copperleaf.ballast.repository.cache.getCachedOrEmptyList
import com.copperleaf.ballast.repository.cache.isLoading
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.jetbrains.compose.web.attributes.ATarget
import org.jetbrains.compose.web.attributes.target
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Hr
import org.jetbrains.compose.web.dom.I
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Text
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
class BggComponent(
    private val injector: ComposeWebInjector
) : Component {
    @Composable
    override fun Content() {
        val viewModelCoroutineScope = rememberCoroutineScope()
        val vm = remember(viewModelCoroutineScope) { injector.bggViewModel(viewModelCoroutineScope) }
        val vmState by vm.observeStates().collectAsState()

        CounterUI(vmState) { vm.trySend(it) }
    }

    @Composable
    private fun CounterUI(
        uiState: BggContract.State,
        postInput: (BggContract.Inputs) -> Unit,
    ) {
        BulmaPanel(
            headingStart = { Text("BoardGameGeek API") },
            headingEnd = {
                A(
                    href = "${ExamplesContext.samplesUrlWithVersion}/bgg",
                    attrs = {
                        title("View sources on GitHub")
                        target(ATarget.Blank)
                    }
                ) {
                    I { Text("Sources") }
                }
            },
        ) {
            if (uiState.bggHotList !is Cached.NotLoaded && uiState.bggHotList.isLoading()) {
                BulmaProgress()
            }

            var forceRefresh by remember { mutableStateOf(false) }

            BulmaSelect(
                fieldName = "HotList Type",
                items = HotListType.values().toList(),
                itemValue = { it.name },
                selectedValue = uiState.bggHotListType,
                onValueChange = { postInput(BggContract.Inputs.ChangeHotListType(it)) },
                itemContent = { Text(it.displayName) }
            )
            BulmaCheckbox(
                fieldName = "Force Refresh",
                value = forceRefresh,
                onValueChange = { forceRefresh = it }
            )
            BulmaButton(
                onClick = { postInput(BggContract.Inputs.FetchHotList(forceRefresh)) }
            ) { Text("Fetch HotList") }

            Hr { }

            uiState
                .bggHotList
                .getCachedOrEmptyList()
                .forEach {
                    BulmaPanel({ Text("${it.rank}: ${it.name}") }) {
                        if (it.yearPublished != null) {
                            Text("Published ${it.yearPublished}")
                        }

                        if (it.thumbnail.isNotBlank()) {
                            Img(src = it.thumbnail)
                        }
                    }
                }
        }
    }
}
