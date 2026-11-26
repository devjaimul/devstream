package com.devsstream.cafepos.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.devsstream.cafepos.ui.cart.CartScreen
import com.devsstream.cafepos.ui.cart.CartViewModel
import com.devsstream.cafepos.ui.cart.PayScreen
import com.devsstream.cafepos.ui.debug.DebugScreen
import com.devsstream.cafepos.ui.menu.ItemScreen
import com.devsstream.cafepos.ui.menu.MenuScreen
import com.devsstream.cafepos.ui.order.OrderScreen
import com.devsstream.cafepos.ui.orders.OrdersScreen
import com.devsstream.cafepos.ui.summary.SummaryScreen

@Composable
fun PosNavHost() {
    val nav = rememberNavController()
    // activity scoped: the cart is shared by menu, item, cart and pay
    val cart: CartViewModel = hiltViewModel()
    val statusVm: StatusViewModel = hiltViewModel()
    val ui by cart.state.collectAsStateWithLifecycle()
    val status by statusVm.status.collectAsStateWithLifecycle()
    val back: () -> Unit = { nav.popBackStack() }

    NavHost(nav, startDestination = "menu") {
        composable("menu") {
            MenuScreen(
                ui = ui,
                status = status,
                onProduct = { nav.navigate("item/$it") },
                onCart = { nav.navigate("cart") },
                onOrders = { nav.navigate("orders") },
                onSummary = { nav.navigate("summary") },
                onDebug = { nav.navigate("debug") },
            )
        }
        composable("item/{productId}") { entry ->
            ItemScreen(
                productId = entry.arguments?.getString("productId").orEmpty(),
                catalog = ui.catalog,
                status = status,
                onAdd = cart::add,
                onBack = back,
            )
        }
        composable("cart") {
            CartScreen(
                ui = ui,
                status = status,
                onQuantity = cart::changeQuantity,
                onCoupon = cart::setCoupon,
                onClear = cart::clear,
                onPay = { nav.navigate("pay") },
                onBack = back,
            )
        }
        composable("pay") {
            PayScreen(ui = ui, status = status, onCash = cart::payCash, onCard = cart::payCard, onBack = back)
        }
        composable("order/{orderId}", arguments = listOf(navArgument("orderId") { type = NavType.LongType })) {
            OrderScreen(status = status, onDone = { nav.popBackStack("menu", inclusive = false) })
        }
        composable("orders") {
            OrdersScreen(status = status, onOpen = { nav.navigate("order/$it") }, onBack = back)
        }
        composable("summary") {
            SummaryScreen(status = status, onBack = back)
        }
        composable("debug") {
            DebugScreen(status = status, onBack = back)
        }
    }

    // A saved sale opens its order screen. Back from there goes to the menu, not to the
    // pay screen of a cart that no longer exists.
    LaunchedEffect(ui.cart.openOrderId) {
        val id = ui.cart.openOrderId ?: return@LaunchedEffect
        nav.navigate("order/$id") { popUpTo("menu") }
        cart.orderOpened()
    }
}
