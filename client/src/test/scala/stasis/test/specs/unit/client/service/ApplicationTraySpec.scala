package stasis.test.specs.unit.client.service

import java.awt.event.ActionEvent
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

import dorkbox.systemTray.Menu
import dorkbox.systemTray.MenuItem
import dorkbox.systemTray.SystemTray
import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar
import org.mockito.captor.ArgCaptor

import stasis.client.service.ApplicationTray
import stasis.test.specs.unit.UnitSpec

class ApplicationTraySpec extends UnitSpec with MockitoSugar {
  "An ApplicationTray" should "load the default tray if supported" in {
    ApplicationTray(
      callbacks = ApplicationTray.Callbacks(terminateService = () => (), startUiService = () => ()),
      systemTray = Some(SystemTray.get())
    ) should be(
      an[ApplicationTray.Default]
    )
  }

  it should "load a no-op tray if not supported" in {
    ApplicationTray(
      callbacks = ApplicationTray.Callbacks(terminateService = () => (), startUiService = () => ()),
      systemTray = None
    ) should be(an[ApplicationTray.NoOp])
  }

  "A Default ApplicationTray" should "provide an application tray" in {
    val terminateCalled = new AtomicBoolean(false)
    val startUiCalled = new AtomicBoolean(false)

    val underlying = mock[SystemTray]
    val menu = mock[Menu]
    val captor = ArgCaptor[MenuItem]

    when(underlying.getMenu).thenReturn(menu)

    val tray = ApplicationTray.Default(
      callbacks = ApplicationTray.Callbacks(
        terminateService = () => terminateCalled.set(true),
        startUiService = () => startUiCalled.set(true)
      ),
      tray = underlying
    )

    tray.init()

    verify(underlying).setTooltip("stasis - Background Service")
    verify(underlying).setStatus("stasis - Background Service")
    verify(underlying).setImage(any[URL]())

    verify(menu, times(2)).add(captor.capture)

    captor.values match {
      case item1 :: item2 :: Nil =>
        item1.getText should be("Show")
        item2.getText should be("Exit")

        startUiCalled.get() should be(false)
        item1.getCallback.actionPerformed(new ActionEvent(this, 0, "test"))
        startUiCalled.get() should be(true)

        terminateCalled.get() should be(false)
        item2.getCallback.actionPerformed(new ActionEvent(this, 0, "test"))
        terminateCalled.get() should be(true)

        tray.shutdown()
        verify(underlying).shutdown()

      case other => fail(s"Unexpected result received: [$other]")
    }
  }

  "A NoOp ApplicationTray" should "do nothing" in {
    noException should be thrownBy ApplicationTray.NoOp().init()
    noException should be thrownBy ApplicationTray.NoOp().shutdown()
  }
}
