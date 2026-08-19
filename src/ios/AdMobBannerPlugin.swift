
import Foundation
import UIKit
import GoogleMobileAds

@objc(AdMobBannerPlugin)
class AdMobBannerPlugin: CDVPlugin, BannerViewDelegate, AdSizeDelegate {

    private var bannerView: BannerView?
    private var isKeyboardVisible = false

    override func pluginInitialize() {
        super.pluginInitialize()
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(keyboardWillShow),
            name: UIResponder.keyboardWillShowNotification,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(keyboardWillHide),
            name: UIResponder.keyboardWillHideNotification,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(orientationDidChange),
            name: UIDevice.orientationDidChangeNotification,
            object: nil
        )
    }

    @objc(create:)
    func create(command: CDVInvokedUrlCommand) {
        guard let adUnitId = command.argument(at: 0) as? String, !adUnitId.isEmpty else {
            let result = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "adUnitId is required")
            self.commandDelegate.send(result, callbackId: command.callbackId)
            return
        }

        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }

            self.destroyBannerView()

            guard let parentView = self.viewController?.view else {
                let result = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "View controller view is unavailable")
                self.commandDelegate.send(result, callbackId: command.callbackId)
                return
            }

            let frameWidth = parentView.frame.inset(by: parentView.safeAreaInsets).width
            let adWidth = frameWidth > 0 ? frameWidth : parentView.frame.width
            let adaptiveSize = largeAnchoredAdaptiveBanner(width: adWidth)

            let banner = BannerView(adSize: adaptiveSize)
            banner.adUnitID = adUnitId
            banner.rootViewController = self.viewController
            banner.delegate = self
            banner.adSizeDelegate = self
            banner.translatesAutoresizingMaskIntoConstraints = false
            banner.isHidden = self.isKeyboardVisible

            parentView.addSubview(banner)

            NSLayoutConstraint.activate([
                banner.bottomAnchor.constraint(equalTo: parentView.safeAreaLayoutGuide.bottomAnchor),
                banner.centerXAnchor.constraint(equalTo: parentView.centerXAnchor)
            ])

            self.bannerView = banner
            banner.load(Request())

            let result = CDVPluginResult(status: CDVCommandStatus_OK)
            self.commandDelegate.send(result, callbackId: command.callbackId)
        }
    }

    @objc(destroy:)
    func destroy(command: CDVInvokedUrlCommand) {
        DispatchQueue.main.async { [weak self] in
            self?.destroyBannerView()
            let result = CDVPluginResult(status: CDVCommandStatus_OK)
            self?.commandDelegate.send(result, callbackId: command.callbackId)
        }
    }

    private func destroyBannerView() {
        bannerView?.delegate = nil
        bannerView?.adSizeDelegate = nil
        bannerView?.removeFromSuperview()
        bannerView = nil
    }

    @objc private func keyboardWillShow(notification: Notification) {
        isKeyboardVisible = true
        bannerView?.isHidden = true
    }

    @objc private func keyboardWillHide(notification: Notification) {
        isKeyboardVisible = false
        bannerView?.isHidden = false
    }

    // MARK: - Orientation Handling

    @objc private func orientationDidChange() {
        guard bannerView != nil else { return }

        DispatchQueue.main.async { [weak self] in
            guard let self = self,
                  let banner = self.bannerView,
                  let parentView = self.viewController?.view else { return }

            let frameWidth = parentView.frame.inset(by: parentView.safeAreaInsets).width
            let adWidth = frameWidth > 0 ? frameWidth : parentView.frame.width
            let newSize = largeAnchoredAdaptiveBanner(width: adWidth)

            // Update ad size and request a new ad for the new orientation
            banner.adSize = newSize
            banner.load(Request())
        }
    }

    // MARK: - BannerViewDelegate

    func bannerViewDidReceiveAd(_ bannerView: BannerView) {
        emitJsEvent(name: "admob.ad.load", detailJson: nil)

        let size = bannerView.adSize.size
        let sizeDict: [String: Any] = [
            "width": size.width,
            "height": size.height
        ]
        if let data = try? JSONSerialization.data(withJSONObject: sizeDict),
           let jsonString = String(data: data, encoding: .utf8) {
            emitJsEvent(name: "admob.ad.resize", detailJson: jsonString)
        }
    }

    func bannerView(_ bannerView: BannerView, didFailToReceiveAdWithError error: Error) {
        let errorMessage = error.localizedDescription
        if let data = try? JSONSerialization.data(withJSONObject: [errorMessage]),
           let jsonArray = String(data: data, encoding: .utf8),
           let escapedString = jsonArray.dropFirst().dropLast() as? Substring {
            emitJsEvent(name: "admob.ad.loadfail", detailJson: String(escapedString))
        } else {
            emitJsEvent(name: "admob.ad.loadfail", detailJson: "\"\(errorMessage)\"")
        }
    }

    // MARK: - AdSizeDelegate

    func adView(_ bannerView: BannerView, willChangeAdSizeTo size: AdSize) {
        let cgSize = size.size
        let sizeDict: [String: Any] = [
            "width": cgSize.width,
            "height": cgSize.height
        ]
        if let data = try? JSONSerialization.data(withJSONObject: sizeDict),
           let jsonString = String(data: data, encoding: .utf8) {
            emitJsEvent(name: "admob.ad.resize", detailJson: jsonString)
        }
    }

    private func emitJsEvent(name: String, detailJson: String?) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            let js: String
            if let detail = detailJson {
                js = "window.admob && window.admob._emitEvent('\(name)', \(detail));"
            } else {
                js = "window.admob && window.admob._emitEvent('\(name)');"
            }
            self.commandDelegate.evalJs(js)
        }
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
        destroyBannerView()
    }
}
