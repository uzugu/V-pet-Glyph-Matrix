import {
  waitForEvenAppBridge,
  CreateStartUpPageContainer,
  RebuildPageContainer,
  ImageContainerProperty,
  TextContainerProperty,
  ImageRawDataUpdate,
  ImageRawDataUpdateResult,
  OsEventTypeList,
  StartUpPageCreateResult,
  type EvenAppBridge,
} from "@evenrealities/even_hub_sdk";
import { encode1BitBmp } from "./BmpEncoder";
export class Display {
  private bridge: EvenAppBridge | null = null;
  private unsubscribe: (() => void) | null = null;
  private serial: Promise<unknown> = Promise.resolve();
  private generation = 0;
  private created = new WeakSet<EvenAppBridge>();
  private ready = false;
  private menuOpen = false;
  get active() {
    return this.ready && !this.menuOpen;
  }
  async connect(
    input: (kind: "press" | "up" | "down" | "double") => void,
    exit: () => void,
  ): Promise<boolean> {
    const generation = ++this.generation;
    this.unsubscribe?.();
    this.ready = false;
    this.menuOpen = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    try {
      const bridge = await Promise.race([
        waitForEvenAppBridge(),
        new Promise<never>((_, reject) => {
          timer = setTimeout(
            () =>
              reject(
                new Error(
                  "Even bridge not detected — browser preview available",
                ),
              ),
            7000,
          );
        }),
      ]);
      if (generation !== this.generation) return false;
      this.bridge = bridge;
      this.unsubscribe = bridge.onEvenHubEvent((event) => {
        if (generation !== this.generation) return;
        const system = event.sysEvent,
          type = system?.eventType ?? event.textEvent?.eventType;
        if (
          type === OsEventTypeList.SYSTEM_EXIT_EVENT ||
          type === OsEventTypeList.ABNORMAL_EXIT_EVENT
        ) {
          this.ready = false;
          ++this.generation;
          this.unsubscribe?.();
          this.unsubscribe = null;
          exit();
          return;
        }
        if (type === OsEventTypeList.FOREGROUND_ENTER_EVENT) {
          this.menuOpen = true;
          return;
        }
        if (type === OsEventTypeList.FOREGROUND_EXIT_EVENT) {
          this.menuOpen = false;
          return;
        }
        if (this.menuOpen) return;
        if (
          type === OsEventTypeList.CLICK_EVENT ||
          (system && system.eventType === undefined)
        )
          input("press");
        if (type === OsEventTypeList.SCROLL_TOP_EVENT) input("up");
        if (type === OsEventTypeList.SCROLL_BOTTOM_EVENT) input("down");
        if (type === OsEventTypeList.DOUBLE_CLICK_EVENT) input("double");
      });
      const page = {
        containerTotalNum: 2,
        textObject: [
          new TextContainerProperty({
            xPosition: 0,
            yPosition: 244,
            width: 576,
            height: 44,
            borderWidth: 0,
            paddingLength: 0,
            containerID: 1,
            containerName: "vpet-input",
            content: " ",
            isEventCapture: 1,
          }),
        ],
        imageObject: [
          new ImageContainerProperty({
            xPosition: 188,
            yPosition: 72,
            width: 200,
            height: 100,
            containerID: 2,
            containerName: "vpet-frame",
          }),
        ],
      };
      const task = this.serial.then(async () => {
        if (generation !== this.generation) return false;
        if (this.created.has(bridge)) {
          if (
            !(await bridge.rebuildPageContainer(new RebuildPageContainer(page)))
          )
            throw new Error("G2 page rebuild failed");
        } else {
          if (
            (await bridge.createStartUpPageContainer(
              new CreateStartUpPageContainer(page),
            )) !== StartUpPageCreateResult.success
          )
            throw new Error("G2 page creation failed");
          this.created.add(bridge);
        }
        if (generation !== this.generation) return false;
        return (this.ready = true);
      });
      this.serial = task.catch(() => {});
      return await task;
    } finally {
      clearTimeout(timer);
    }
  }
  async draw(pixels: Uint8Array): Promise<void> {
    const generation = this.generation,
      bridge = this.bridge,
      bytes = encode1BitBmp(200, 100, pixels);
    if (!bridge || !this.active) return;
    const task = this.serial.then(async () => {
      if (generation !== this.generation || !this.active) return;
      const result = await bridge.updateImageRawData(
        new ImageRawDataUpdate({
          containerID: 2,
          containerName: "vpet-frame",
          imageData: bytes,
        }),
      );
      if (result !== ImageRawDataUpdateResult.success)
        throw new Error("Glasses image update failed: " + result);
    });
    this.serial = task.catch(() => {});
    await task;
  }
}
