package com.pystelectronic.rfid.transfer.mapper;

import com.pystelectronic.rfid.common.dto.response.*;
import com.pystelectronic.rfid.transfer.entity.*;
import org.mapstruct.*;
import java.util.List;

@Mapper(componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface TransferMapper {

    @Mapping(target = "transferId", expression = "java(transfer.getId().toString())")
    @Mapping(target = "pallets", ignore = true)
    TransferResponse toResponse(Transfer transfer);

    @Mapping(target = "transferId", expression = "java(transfer.getId().toString())")
    @Mapping(target = "pallets", source = "pallets", qualifiedByName = "toPalletDetailedResponseList")
    TransferResponse toDetailedResponse(Transfer transfer);

    @Named("toPalletResponse")
    @Mapping(target = "palletId", expression = "java(pallet.getId().toString())")
    @Mapping(target = "transferId", expression = "java(pallet.getTransfer().getId().toString())")
    @Mapping(target = "contents", ignore = true)
    PalletResponse toPalletResponse(Pallet pallet);

    @Named("toPalletDetailedResponse")
    @Mapping(target = "palletId", expression = "java(pallet.getId().toString())")
    @Mapping(target = "transferId", expression = "java(pallet.getTransfer().getId().toString())")
    @Mapping(target = "contents", expression = "java(mapPalletContents(pallet))")
    PalletResponse toPalletDetailedResponse(Pallet pallet);

    @IterableMapping(qualifiedByName = "toPalletDetailedResponse")
    @Named("toPalletDetailedResponseList")
    List<PalletResponse> toPalletDetailedResponseList(List<Pallet> pallets);

    @Mapping(target = "contentType", constant = "LPN")
    @Mapping(target = "lpnId", expression = "java(lpn.getId().toString())")
    @Mapping(target = "skus", source = "skus")
    @Mapping(target = "looseItemId", ignore = true)
    @Mapping(target = "skuCode", ignore = true)
    @Mapping(target = "skuDescription", ignore = true)
    @Mapping(target = "unitQuantity", ignore = true)
    ContentItemResponse toContentItemResponse(Lpn lpn);

    @Mapping(target = "contentType", constant = "LOOSE_ITEM")
    @Mapping(target = "looseItemId", expression = "java(item.getId().toString())")
    @Mapping(target = "lpnId", ignore = true)
    @Mapping(target = "lpnCode", ignore = true)
    @Mapping(target = "epc", ignore = true)
    @Mapping(target = "isKit", ignore = true)
    @Mapping(target = "piecesInside", ignore = true)
    @Mapping(target = "skus", ignore = true)
    @Mapping(target = "totalUnits", source = "unitQuantity")
    ContentItemResponse toContentItemResponseFromLooseItem(LooseItem item);

    ContentItemResponse.SkuResponse toSkuResponse(LpnSku sku);

    List<ContentItemResponse.SkuResponse> toSkuResponseList(List<LpnSku> skus);

    default List<ContentItemResponse> mapPalletContents(Pallet pallet) {
        java.util.List<ContentItemResponse> contents = new java.util.ArrayList<>();
        if (pallet.getLpns() != null) {
            pallet.getLpns().forEach(lpn -> contents.add(toContentItemResponse(lpn)));
        }
        if (pallet.getLooseItems() != null) {
            pallet.getLooseItems().forEach(item -> contents.add(toContentItemResponseFromLooseItem(item)));
        }
        return contents;
    }
}