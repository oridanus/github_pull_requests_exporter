import com.google.inject.AbstractModule
import services.ExportRunner

class Module extends AbstractModule {

  override def configure() = {
    bind(classOf[ExportRunner]).asEagerSingleton()
  }

}
